// src/main/java/brito/com/multitenancy001/infrastructure/security/userdetails/MultiContextUserDetailsService.java
package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * UserDetailsService multi-contexto (Tenant + Control Plane).
 *
 * Regras:
 * - NÃO injeta EntityManager "genérico" (há múltiplos EMFs: public + tenant).
 * - Usa EntityManagerFactory qualificado para criar EntityManager sob demanda (fecha sempre).
 * - Tenant queries usam o EMF do TENANT (multi-tenancy por schema via resolver do Hibernate).
 * - Public queries usam o EMF do PUBLIC (schema public/control-plane).
 * - Não usa Instant.now(): usa AppClock.
 *
 * Observação (importante para o seu 401 pós-refactor):
 * - Se você consultar TenantUser usando o EMF/EM do PUBLIC, o resultado será sempre "não encontrado",
 *   e o login tenant vira 401 mesmo após signup.
 * - Este serviço garante explicitamente o EMF correto em cada consulta.
 */
@Service
@RequiredArgsConstructor
public class MultiContextUserDetailsService implements UserDetailsService {

    /**
     * EMF do PUBLIC (Control Plane).
     * Nome do bean vem do seu PublicPersistenceConfig: "publicEntityManagerFactory".
     */
    private final @Qualifier("publicEntityManagerFactory") EntityManagerFactory publicEmf;

    /**
     * EMF do TENANT (schema-per-tenant).
     * Nome do bean vem do seu TenantSchemaHibernateConfig: "tenantEntityManagerFactory".
     */
    private final @Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEmf;

    private final AppClock appClock;

    @Override
    public UserDetails loadUserByUsername(String username) {
        /*
         * Método padrão do Spring.
         * No seu projeto, o fluxo real usa os métodos loadTenantUserByEmail/loadControlPlaneUserByEmail.
         * Aqui mantemos um fallback tenant "sem accountId" (busca por email) para compat.
         */
        return loadTenantUserByEmail(username, null);
    }

    /**
     * Usado por JwtAuthenticationFilter (tenant) / fluxos tenant.
     */
    public UserDetails loadTenantUserByEmail(String email, Long accountId) {
        /* Carrega tenant user (no schema tenant atual) e retorna AuthenticatedUserContext. */
        if (email == null || email.isBlank()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Email é obrigatório");
        }

        Instant now = appClock.instant();
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        TenantUser user = withTenantEntityManager(em -> {
            if (accountId == null) {
                return em.createQuery(
                                "select u from TenantUser u " +
                                        "where lower(u.email) = :email and u.deleted = false",
                                TenantUser.class
                        )
                        .setParameter("email", normalized)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));
            }

            return em.createQuery(
                            "select u from TenantUser u " +
                                    "where lower(u.email) = :email and u.accountId = :accountId and u.deleted = false",
                            TenantUser.class
                    )
                    .setParameter("email", normalized)
                    .setParameter("accountId", accountId)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));
        });

        // tenantSchema: em muitos fluxos o schema já está em claims/thread-local; aqui mantemos null (compat).
        String tenantSchema = null;

        // Authorities: deixe o fromTenantUser montar o fallback se set vazio.
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        return AuthenticatedUserContext.fromTenantUser(user, tenantSchema, now, authorities);
    }

    /**
     * Usado por JwtAuthenticationFilter / refresh do Control Plane.
     */
    public UserDetails loadControlPlaneUserByEmail(String email, Long userId) {
        /*
         * Importante:
         * - Aqui retornamos AuthenticatedUserContext também (ele implementa UserDetails),
         *   pois o resto do stack faz cast para AuthenticatedUserContext.
         */
        if (email == null || email.isBlank()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Email é obrigatório");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        Object cpUser = withPublicEntityManager(em ->
                em.createQuery(
                                "select u from ControlPlaneUser u " +
                                        "where lower(u.email) = :email and u.deleted = false",
                                Object.class
                        )
                        .setParameter("email", normalized)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"))
        );

        // Extrai dados via reflexão para evitar dependência do tipo concreto aqui.
        Long resolvedUserId = (userId != null) ? userId : ReflectionSupport.getLong(cpUser, "getId");
        String roleName = ReflectionSupport.getString(cpUser, "getRole");
        String roleAuthority = ReflectionSupport.getString(cpUser, "getRoleAuthority");

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (roleAuthority != null && !roleAuthority.isBlank()) {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(roleAuthority));
        }

        return AuthenticatedUserContext.fromControlPlaneClaims(
                resolvedUserId != null ? resolvedUserId : 0L,
                normalized,
                roleName,
                roleAuthority,
                authorities
        );
    }

    /**
     * Executa uma função usando um EntityManager do PUBLIC, garantindo close.
     */
    private <T> T withPublicEntityManager(EntityManagerCallback<T> cb) {
        /* Cria EntityManager do PUBLIC e fecha sempre. */
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
     * Executa uma função usando um EntityManager do TENANT, garantindo close.
     */
    private <T> T withTenantEntityManager(EntityManagerCallback<T> cb) {
        /* Cria EntityManager do TENANT e fecha sempre. */
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
     * Callback tipado para uso com EntityManager.
     */
    @FunctionalInterface
    private interface EntityManagerCallback<T> {
        /* Aplica operação com EntityManager. */
        T apply(EntityManager em);
    }

    /**
     * Helper de reflexão (isolado).
     */
    static final class ReflectionSupport {
        private ReflectionSupport() {}

        static Long getLong(Object target, String methodName) {
            try {
                Object v = target.getClass().getMethod(methodName).invoke(target);
                if (v == null) return null;
                if (v instanceof Long l) return l;
                if (v instanceof Integer i) return i.longValue();
                if (v instanceof Number n) return n.longValue();
                return null;
            } catch (Exception ignored) {
                return null;
            }
        }

        static String getString(Object target, String methodName) {
            try {
                Object v = target.getClass().getMethod(methodName).invoke(target);
                if (v == null) return null;
                return String.valueOf(v);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}