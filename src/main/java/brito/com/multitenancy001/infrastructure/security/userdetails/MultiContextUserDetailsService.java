package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * UserDetailsService multi-contexto (Tenant + Control Plane).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver o tipo de usuário (Control Plane vs Tenant) via tabela {@code login_identities}.</li>
 *   <li>Carregar o usuário apropriado do schema correto.</li>
 *   <li>Retornar um {@link AuthenticatedUserContext} que implementa {@link UserDetails}.</li>
 * </ul>
 *
 * <p>Fluxo de Resolução:</p>
 * <ol>
 *   <li>Consulta a tabela {@code public.login_identities} pelo email para obter o {@code subject_type}.</li>
 *   <li>Se {@code subject_type} for {@code CONTROLPLANE_USER}, carrega do schema PUBLIC.</li>
 *   <li>Se {@code subject_type} for {@code TENANT_USER}, carrega do schema do tenant (que deve estar bindado via {@code X-Tenant}).</li>
 *   <li>Fallback: se não encontrar na identidade, tenta carregar como Tenant (compatibilidade).</li>
 * </ol>
 *
 * <p>Regras de Tempo:</p>
 * <ul>
 *   <li>Não usa {@code Instant.now()}. A fonte de tempo é {@link AppClock}.</li>
 * </ul>
 *
 * <p>Tratamento de Erros:</p>
 * <ul>
 *   <li>Se o usuário não for encontrado, lança {@link UsernameNotFoundException} (compatível com Spring Security).</li>
 *   <li>Erros internos são encapsulados em {@link ApiException}.</li>
 * </ul>
 *
 * @see AuthenticatedUserContext
 * @see AuthoritiesFactory
 */
@Service
@RequiredArgsConstructor
public class MultiContextUserDetailsService implements UserDetailsService {

    private static final String SUBJECT_TYPE_CONTROLPLANE_USER = "CONTROLPLANE_USER";
    private static final String SUBJECT_TYPE_TENANT_USER = "TENANT_USER";

    /**
     * EMF do PUBLIC (Control Plane).
     */
    private final @Qualifier("publicEntityManagerFactory") EntityManagerFactory publicEmf;

    /**
     * EMF do TENANT (schema-per-tenant).
     */
    private final @Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEmf;

    private final AppClock appClock;

    /**
     * Carrega um usuário pelo username (email) conforme contrato do Spring Security.
     *
     * @param username O email do usuário.
     * @return Um {@link UserDetails} representando o usuário autenticado.
     * @throws UsernameNotFoundException Se o usuário não for encontrado.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Email é obrigatório");
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);

        try {
            String subjectType = resolveSubjectTypeByEmail(normalized);

            if (SUBJECT_TYPE_CONTROLPLANE_USER.equals(subjectType)) {
                return loadControlPlaneUserByEmail(normalized, null);
            }

            if (SUBJECT_TYPE_TENANT_USER.equals(subjectType)) {
                return loadTenantUserByEmail(normalized, null);
            }

            // Fallback: tenta carregar como Tenant (compatibilidade)
            return loadTenantUserByEmail(normalized, null);

        } catch (ApiException e) {
            throw new UsernameNotFoundException(e.getMessage(), e);
        }
    }

    /**
     * Resolve o tipo de sujeito (subject_type) a partir do email, consultando a tabela login_identities.
     *
     * @param normalizedEmail Email normalizado.
     * @return O tipo de sujeito, ou {@code null} se não encontrado.
     */
    private String resolveSubjectTypeByEmail(String normalizedEmail) {
        return withPublicEntityManager(em -> {
            try {
                // Abordagem 1: Usando getSingleResult com tratamento de exceção
                String sql = """
                    SELECT li.subject_type
                    FROM public.login_identities li
                    WHERE li.email = :email
                """;

                @SuppressWarnings("unchecked")
                List<Object> results = em.createNativeQuery(sql)
                        .setParameter("email", normalizedEmail)
                        .setMaxResults(1)
                        .getResultList();

                if (results.isEmpty()) {
                    return null;
                }

                Object result = results.get(0);
                return result != null ? result.toString() : null;

            } catch (Exception e) {
                // Log do erro se necessário, mas não propaga
                return null;
            }
        });
    }

    /**
     * Carrega um usuário do Tenant pelo email e accountId.
     *
     * @param email     O email do usuário.
     * @param accountId O ID da conta (pode ser {@code null} se não fornecido).
     * @return Um {@link UserDetails} representando o usuário do tenant.
     * @throws ApiException Se o usuário não for encontrado.
     */
    public UserDetails loadTenantUserByEmail(String email, Long accountId) {
        if (email == null || email.isBlank()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Email é obrigatório");
        }

        Instant now = appClock.instant();
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        TenantUser user = withTenantEntityManager(em -> {
            TypedQuery<TenantUser> query;

            if (accountId == null) {
                query = em.createQuery(
                        "SELECT u FROM TenantUser u WHERE LOWER(u.email) = :email AND u.deleted = false",
                        TenantUser.class
                );
            } else {
                query = em.createQuery(
                        "SELECT u FROM TenantUser u WHERE LOWER(u.email) = :email AND u.accountId = :accountId AND u.deleted = false",
                        TenantUser.class
                );
                query.setParameter("accountId", accountId);
            }

            query.setParameter("email", normalized);
            query.setMaxResults(1);

            try {
                return query.getSingleResult();
            } catch (NoResultException e) {
                throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado");
            }
        });

        String tenantSchema = null; // Será preenchido pelo JwtAuthenticationFilter
        var authorities = AuthoritiesFactory.forTenant(user);

        return AuthenticatedUserContext.fromTenantUser(user, tenantSchema, now, authorities);
    }

    /**
     * Carrega um usuário do Control Plane pelo email e userId.
     *
     * @param email  O email do usuário.
     * @param userId O ID do usuário (pode ser {@code null} se não fornecido).
     * @return Um {@link UserDetails} representando o usuário do Control Plane.
     * @throws ApiException Se o usuário não for encontrado.
     */
    public UserDetails loadControlPlaneUserByEmail(String email, Long userId) {
        if (email == null || email.isBlank()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Email é obrigatório");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        ControlPlaneUser user = withPublicEntityManager(em -> {
            TypedQuery<ControlPlaneUser> query = em.createQuery(
                    "SELECT u FROM ControlPlaneUser u WHERE LOWER(u.email) = :email AND u.deleted = false",
                    ControlPlaneUser.class
            );
            query.setParameter("email", normalized);
            query.setMaxResults(1);

            try {
                return query.getSingleResult();
            } catch (NoResultException e) {
                throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado");
            }
        });

        var authorities = AuthoritiesFactory.forControlPlane(user);

        return AuthenticatedUserContext.fromControlPlaneClaims(
                user.getId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getRole() != null ? user.getRole().asAuthority() : null,
                authorities
        );
    }

    /**
     * Executa uma função usando um EntityManager do PUBLIC, garantindo fechamento.
     *
     * @param cb A função a ser executada.
     * @param <T> O tipo de retorno.
     * @return O resultado da função.
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
     * Executa uma função usando um EntityManager do TENANT, garantindo fechamento.
     *
     * @param cb A função a ser executada.
     * @param <T> O tipo de retorno.
     * @return O resultado da função.
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

    /**
     * Callback funcional para operações com EntityManager.
     *
     * @param <T> O tipo de retorno.
     */
    @FunctionalInterface
    private interface EntityManagerCallback<T> {
        T apply(EntityManager em);
    }
}