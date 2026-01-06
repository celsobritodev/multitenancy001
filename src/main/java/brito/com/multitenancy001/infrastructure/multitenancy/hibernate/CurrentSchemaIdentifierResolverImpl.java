package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class CurrentSchemaIdentifierResolverImpl
        implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_SCHEMA = "public";
    private static final ThreadLocal<String> TENANT_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Bind do tenant na thread atual.
     * - Se vier vazio/nulo: remove o tenant (fica "sem tenant", e o resolver decide fallback).
     * - Se vier preenchido: seta no ThreadLocal.
     */
    public static void bindTenantToCurrentThread(String tenantId) {
        String previous = TENANT_THREAD_LOCAL.get();

        String normalized = (tenantId != null ? tenantId.trim() : null);

        if (StringUtils.hasText(normalized)) {
            TENANT_THREAD_LOCAL.set(normalized);
            if (!normalized.equals(previous)) {
                log.info("🔄 Tenant bindado à thread: {} -> {}", previous, normalized);
            } else {
                log.debug("🔄 Tenant já estava bindado: {}", normalized);
            }
        } else {
            TENANT_THREAD_LOCAL.remove();
            if (previous != null) {
                log.info("🧹 Tenant removido da thread (anterior: {})", previous);
            } else {
                log.debug("🧹 Tenant já estava vazio (nada para remover)");
            }
        }
    }

    /**
     * Retorna o tenant REALMENTE bindado.
     * ✅ Importante: aqui retornamos null quando não há tenant,
     * pra não mascarar estado e facilitar debug.
     */
    public static String resolveBoundTenantOrNull() {
        String t = TENANT_THREAD_LOCAL.get();
        return StringUtils.hasText(t) ? t : null;
    }

    /**
     * Mantém compatibilidade com seu código atual (ex.: logs do provider).
     * Use isso somente quando você quer um fallback explícito para public.
     */
    public static String resolveBoundTenantOrDefault() {
        String t = resolveBoundTenantOrNull();
        return (t != null ? t : DEFAULT_SCHEMA);
    }

    public static void unbindTenantFromCurrentThread() {
        String previous = TENANT_THREAD_LOCAL.get();
        TENANT_THREAD_LOCAL.remove();
        if (previous != null) {
            log.info("🧹 Tenant desbindado da thread (anterior: {})", previous);
        } else {
            log.debug("🧹 Tenant desbindado (já estava vazio)");
        }
    }

    /**
     * O Hibernate sempre precisa de um tenant válido.
     * ✅ Aqui sim a gente aplica fallback para DEFAULT_TENANT.
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = resolveBoundTenantOrNull();
        String resolved = (tenant != null ? tenant : DEFAULT_SCHEMA);

        if (log.isDebugEnabled()) {
            log.debug("🏷️ Hibernate resolveu tenant={} (bound={}, default={})",
                    resolved, tenant, DEFAULT_SCHEMA);
        }
        return resolved;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(String tenantIdentifier) {
        return DEFAULT_SCHEMA.equals(tenantIdentifier);
    }
}
