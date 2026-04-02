package brito.com.multitenancy001.infrastructure.security.userdetails;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UserDetailsService multi-contexto.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver o tipo de sujeito via tabela public.login_identities.</li>
 *   <li>Suportar login do control plane com password hash.</li>
 *   <li>Suportar reconstrução do principal do tenant/control plane para fluxos JWT.</li>
 * </ul>
 *
 * <p>Observações importantes:</p>
 * <ul>
 *   <li>Os métodos JWT retornam principal de segurança pronto para o filtro.</li>
 *   <li>Os métodos de tenant assumem {@link TenantContext} já bindado.</li>
 *   <li>Esta implementação usa somente métodos que realmente existem no domínio atual.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiContextUserDetailsService implements UserDetailsService {

    private static final String SUBJECT_TYPE_CONTROLPLANE_USER = "CONTROLPLANE_USER";
    private static final String SUBJECT_TYPE_TENANT_USER = "TENANT_USER";

    private final @Qualifier("publicEntityManagerFactory") EntityManagerFactory publicEmf;
    private final @Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEmf;
    private final AppClock appClock;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Email é obrigatório");
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        String subjectType = resolveSubjectTypeByEmail(normalized);

        if (SUBJECT_TYPE_CONTROLPLANE_USER.equals(subjectType)) {
            return loadControlPlaneUserForLoginByEmail(normalized);
        }

        if (SUBJECT_TYPE_TENANT_USER.equals(subjectType)) {
            return loadTenantPrincipalByEmail(normalized, null);
        }

        throw new UsernameNotFoundException("Usuário não encontrado");
    }

    /**
     * Loader para JWT/control plane.
     *
     * @param email email normalizado
     * @param accountId id da conta
     * @return principal autenticado do control plane
     */
    public UserDetails loadControlPlaneUserByEmail(String email, Long accountId) {
        ControlPlaneUser user = findControlPlaneUserOrThrow(email, accountId);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(AuthoritiesFactory.forControlPlane(user));

        String roleName = user.getRole() != null ? user.getRole().name() : null;
        String roleAuthority = user.getRole() != null ? user.getRole().asAuthority() : null;

        Long userId = user.getId();
        if (userId == null) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário do control plane inválido", 401);
        }

        return AuthenticatedUserContext.fromControlPlaneClaims(
                userId,
                user.getEmail(),
                roleName,
                roleAuthority,
                authorities
        );
    }

    /**
     * Loader para JWT/tenant.
     *
     * @param email email normalizado
     * @param accountId id da conta
     * @param tenantSchema schema esperado
     * @return principal autenticado do tenant
     */
    public AuthenticatedUserContext loadTenantAuthenticatedUserByEmail(
            String email,
            Long accountId,
            String tenantSchema
    ) {
        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "tenantSchema é obrigatório", 401);
        }

        String boundTenant = TenantContext.requireTenant();
        if (!tenantSchema.equalsIgnoreCase(boundTenant)) {
            throw new ApiException(
                    ApiErrorCode.UNAUTHENTICATED,
                    "TenantContext incompatível com o tenant esperado",
                    401
            );
        }

        TenantUser user = findTenantUserOrThrow(email, accountId);
        ensureTenantUserActive(user);

        return AuthenticatedUserContext.fromTenantUser(
                user,
                tenantSchema,
                appClock.instant(),
                AuthoritiesFactory.forTenant(user)
        );
    }

    /**
     * Loader auxiliar que retorna principal Spring do tenant.
     *
     * @param email email normalizado
     * @param accountId id da conta opcional
     * @return principal Spring do tenant
     */
    public UserDetails loadTenantPrincipalByEmail(String email, Long accountId) {
        TenantUser user = findTenantUserOrThrow(email, accountId);
        ensureTenantUserActive(user);
        return new TenantUserPrincipal(user, appClock);
    }

    /**
     * Resolve o subject type via public.login_identities.
     *
     * @param normalizedEmail email normalizado
     * @return subject type ou null
     */
    private String resolveSubjectTypeByEmail(String normalizedEmail) {
        return withPublicEntityManager(em -> {
            @SuppressWarnings("unchecked")
            List<Object> rows = em.createNativeQuery("""
                    SELECT li.subject_type
                    FROM public.login_identities li
                    WHERE li.email = :email
                    """)
                    .setParameter("email", normalizedEmail)
                    .setMaxResults(1)
                    .getResultList();

            if (rows == null || rows.isEmpty() || rows.get(0) == null) {
                return null;
            }

            return rows.get(0).toString();
        });
    }

    /**
     * Carrega usuário do control plane para fluxo de login tradicional.
     *
     * @param email email normalizado
     * @return UserDetails com hash de senha
     */
    private UserDetails loadControlPlaneUserForLoginByEmail(String email) {
        ControlPlaneUser user = withPublicEntityManager(em -> {
            TypedQuery<ControlPlaneUser> query = em.createQuery("""
                    select u
                    from ControlPlaneUser u
                    where lower(u.email) = :email
                    """, ControlPlaneUser.class);
            query.setParameter("email", email);
            return query.getResultStream().findFirst().orElse(null);
        });

        if (user == null) {
            throw new UsernameNotFoundException("Usuário do control plane não encontrado");
        }

        String passwordHash = user.getPassword();
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new UsernameNotFoundException("Usuário do control plane sem password hash");
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(AuthoritiesFactory.forControlPlane(user));

        return User.withUsername(user.getEmail())
                .password(passwordHash)
                .authorities(authorities)
                .accountLocked(!isControlPlaneUserNonLocked(user))
                .disabled(!isControlPlaneUserEnabled(user))
                .build();
    }

    /**
     * Busca usuário do control plane e valida se está apto para autenticação.
     *
     * @param email email normalizado
     * @param accountId id da conta
     * @return usuário do control plane
     */
    private ControlPlaneUser findControlPlaneUserOrThrow(String email, Long accountId) {
        ControlPlaneUser user = withPublicEntityManager(em -> {
            TypedQuery<ControlPlaneUser> query = em.createQuery("""
                    select u
                    from ControlPlaneUser u
                    where lower(u.email) = :email
                      and (:accountId is null or u.account.id = :accountId)
                    """, ControlPlaneUser.class);
            query.setParameter("email", email);
            query.setParameter("accountId", accountId);
            return query.getResultStream().findFirst().orElse(null);
        });

        if (user == null) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário do control plane não encontrado", 401);
        }

        if (!isControlPlaneUserEnabled(user) || !isControlPlaneUserNonLocked(user)) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário do control plane inativo", 401);
        }

        return user;
    }

    /**
     * Busca usuário do tenant no schema já bindado.
     *
     * @param email email normalizado
     * @param accountId id da conta
     * @return usuário do tenant
     */
    private TenantUser findTenantUserOrThrow(String email, Long accountId) {
        TenantUser user = withTenantEntityManager(em -> {
            TypedQuery<TenantUser> query = em.createQuery("""
                    select u
                    from TenantUser u
                    where lower(u.email) = :email
                      and (:accountId is null or u.accountId = :accountId)
                      and u.deleted = false
                    """, TenantUser.class);
            query.setParameter("email", email);
            query.setParameter("accountId", accountId);
            return query.getResultStream().findFirst().orElse(null);
        });

        if (user == null) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário do tenant não encontrado", 401);
        }

        return user;
    }

    /**
     * Valida se o usuário tenant está ativo.
     *
     * @param user usuário tenant
     */
    private void ensureTenantUserActive(TenantUser user) {
        if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário do tenant inativo", 401);
        }
    }

    /**
     * Verifica se usuário do control plane está habilitado.
     *
     * @param user usuário do control plane
     * @return true quando habilitado
     */
    private boolean isControlPlaneUserEnabled(ControlPlaneUser user) {
        try {
            return user.isEnabled();
        } catch (Exception e) {
            log.warn("Falha ao avaliar isEnabled do control plane user id={} motivo={}",
                    user != null ? user.getId() : null,
                    e.getMessage());
            return true;
        }
    }

    /**
     * Verifica se usuário do control plane não está bloqueado.
     *
     * @param user usuário do control plane
     * @return true quando não bloqueado
     */
    private boolean isControlPlaneUserNonLocked(ControlPlaneUser user) {
        try {
            return user.isAccountNonLocked(appClock.instant());
        } catch (Exception e) {
            log.warn("Falha ao avaliar lock do control plane user id={} motivo={}",
                    user != null ? user.getId() : null,
                    e.getMessage());
            return true;
        }
    }

    /**
     * Executa callback com EntityManager do public schema.
     *
     * @param cb callback
     * @return resultado
     * @param <T> tipo de retorno
     */
    private <T> T withPublicEntityManager(EntityManagerCallback<T> cb) {
        EntityManager em = publicEmf.createEntityManager();
        try {
            return cb.apply(em);
        } finally {
            try {
                em.close();
            } catch (Exception ignored) {
                // no-op
            }
        }
    }

    /**
     * Executa callback com EntityManager tenant.
     *
     * @param cb callback
     * @return resultado
     * @param <T> tipo de retorno
     */
    private <T> T withTenantEntityManager(EntityManagerCallback<T> cb) {
        EntityManager em = tenantEmf.createEntityManager();
        try {
            return cb.apply(em);
        } finally {
            try {
                em.close();
            } catch (Exception ignored) {
                // no-op
            }
        }
    }

    @FunctionalInterface
    private interface EntityManagerCallback<T> {
        T apply(EntityManager em);
    }
}