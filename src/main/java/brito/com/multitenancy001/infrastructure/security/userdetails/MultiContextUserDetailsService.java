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
import org.springframework.security.core.userdetails.User;
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
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Resolver o "tipo de sujeito" (Tenant vs Control Plane) via tabela <code>public.login_identities</code>.</li>
 *   <li>No caso Control Plane (LOGIN com AuthenticationManager/DaoAuthenticationProvider):
 *       retornar um <code>org.springframework.security.core.userdetails.User</code> com password hash.</li>
 *   <li>No caso JWT/refresh/filtros: disponibilizar loaders que retornam <code>AuthenticatedUserContext</code>.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Control Plane login exige password hash em <code>UserDetails</code> (senão ocorre "Empty encoded password").</li>
 *   <li>Tenant login normalmente não passa por AuthenticationManager (por causa de schema-per-tenant e resolução de tenant),
 *       então <code>loadUserByUsername</code> é focado em suportar o Control Plane com segurança.</li>
 * </ul>
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
     * Método padrão do Spring Security para resolver o usuário por "username".
     * Aqui, o username é o email.
     *
     * <p>Regra crítica:</p>
     * <ul>
     *   <li>Se for CONTROLPLANE_USER: retorna <code>User</code> (com password hash) para o AuthenticationManager.</li>
     *   <li>Se for TENANT_USER: mantém fallback para carregar algo (evita quebrar chamadas antigas),
     *       mas não é o caminho ideal para login de tenant.</li>
     * </ul>
     *
     * @param username email (username) recebido pelo Spring Security.
     * @return UserDetails apropriado.
     * @throws UsernameNotFoundException se não encontrado/ inválido.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        /** comentário: valida e normaliza o username/email para resolver subject_type e carregar o UserDetails correto */
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Email é obrigatório");
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);

        try {
            String subjectType = resolveSubjectTypeByEmail(normalized);

            // ✅ caminho correto para LOGIN do Control Plane (AuthenticationManager precisa de password hash)
            if (SUBJECT_TYPE_CONTROLPLANE_USER.equals(subjectType)) {
                return loadControlPlaneUserForLoginByEmail(normalized);
            }

            // Tenant: fallback para compatibilidade (não é recomendado como caminho de login em schema-per-tenant)
            if (SUBJECT_TYPE_TENANT_USER.equals(subjectType)) {
                return loadTenantUserByEmail(normalized, null);
            }

            // fallback final: tenta tenant para não quebrar fluxos antigos
            return loadTenantUserByEmail(normalized, null);

        } catch (ApiException e) {
            throw new UsernameNotFoundException(e.getMessage(), e);
        }
    }

    /**
     * Resolve o tipo de sujeito (subject_type) a partir do email, consultando a tabela public.login_identities.
     *
     * @param normalizedEmail Email normalizado.
     * @return subject_type (ou null se não encontrado).
     */
    private String resolveSubjectTypeByEmail(String normalizedEmail) {

        /** comentário: consulta public.login_identities para descobrir se o email pertence a CONTROLPLANE_USER ou TENANT_USER */
        return withPublicEntityManager(em -> {
            try {
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

            } catch (Exception ignored) {
                return null;
            }
        });
    }

    /**
     * Carrega um usuário do Tenant pelo email e accountId.
     *
     * @param email     Email.
     * @param accountId accountId opcional.
     * @return UserDetails (AuthenticatedUserContext) do tenant.
     */
    public UserDetails loadTenantUserByEmail(String email, Long accountId) {

        /** comentário: carrega TenantUser do schema tenant (depende de tenant schema já resolvido no contexto da app) */
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

        String tenantSchema = null; // preenchido pelo JwtAuthenticationFilter
        var authorities = AuthoritiesFactory.forTenant(user);

        return AuthenticatedUserContext.fromTenantUser(user, tenantSchema, now, authorities);
    }

    /**
     * Carrega um usuário do Control Plane como "contexto autenticado" (AuthenticatedUserContext).
     * Use este método para JWT/refresh/filtros, não para login via AuthenticationManager.
     *
     * @param email  Email.
     * @param userId userId opcional (mantido por compat).
     * @return AuthenticatedUserContext (como UserDetails).
     */
    public UserDetails loadControlPlaneUserByEmail(String email, Long userId) {

        /** comentário: carrega ControlPlaneUser no schema public e monta AuthenticatedUserContext (sem password) */
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
     * Carrega um usuário do Control Plane para LOGIN (AuthenticationManager).
     * Retorna UserDetails do Spring com password hash do banco.
     *
     * @param normalizedEmail email já normalizado.
     * @return UserDetails com password hash.
     */
    public UserDetails loadControlPlaneUserForLoginByEmail(String normalizedEmail) {

        /** comentário: carrega ControlPlaneUser e devolve org.springframework.security.core.userdetails.User com password hash */
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Email é obrigatório");
        }

        ControlPlaneUser user = withPublicEntityManager(em -> {
            TypedQuery<ControlPlaneUser> query = em.createQuery(
                    "SELECT u FROM ControlPlaneUser u WHERE LOWER(u.email) = :email AND u.deleted = false",
                    ControlPlaneUser.class
            );
            query.setParameter("email", normalizedEmail);
            query.setMaxResults(1);

            try {
                return query.getSingleResult();
            } catch (NoResultException e) {
                throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado");
            }
        });

        String passwordHash = user.getPassword();
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_USER, "Usuário sem password hash cadastrado");
        }

        var authorities = AuthoritiesFactory.forControlPlane(user);

        return User.withUsername(user.getEmail())
                .password(passwordHash)
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    /**
     * Executa callback com EntityManager do schema PUBLIC (Control Plane), garantindo fechamento.
     *
     * @param cb callback.
     * @return resultado.
     * @param <T> tipo.
     */
    private <T> T withPublicEntityManager(EntityManagerCallback<T> cb) {

        /** comentário: cria/fecha EntityManager do publicEmf para execução segura de queries */
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
     * Executa callback com EntityManager do TENANT, garantindo fechamento.
     *
     * @param cb callback.
     * @return resultado.
     * @param <T> tipo.
     */
    private <T> T withTenantEntityManager(EntityManagerCallback<T> cb) {

        /** comentário: cria/fecha EntityManager do tenantEmf para execução segura de queries */
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
     * @param <T> tipo do retorno.
     */
    @FunctionalInterface
    private interface EntityManagerCallback<T> {
        T apply(EntityManager em);
    }
}