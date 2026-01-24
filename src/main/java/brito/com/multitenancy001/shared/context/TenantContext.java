package brito.com.multitenancy001.shared.context;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.multitenancy.hibernate.CurrentTenantSchemaResolver;
import brito.com.multitenancy001.shared.db.Schemas;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    public static final String PUBLIC_SCHEMA = Schemas.CONTROL_PLANE;

    /**
     * ✅ Retorna o tenant REALMENTE bindado (ou null).
     * Não mascara com Schemas.CONTROL_PLANE.
     */
    public static String getOrNull() {
        return CurrentTenantSchemaResolver.resolveBoundTenantOrNull();
    }

    /**
     * ✅ Quando você quer um fallback explícito para public.
     * (Útil pra logs/diagnóstico; no runtime o "public" é representado por null.)
     */
    public static String getOrDefaultPublic() {
        String t = getOrNull();
        return (t != null ? t : PUBLIC_SCHEMA);
    }

    public static boolean isPublic() {
        String t = getOrNull();
        return t == null || PUBLIC_SCHEMA.equalsIgnoreCase(t);
    }

    public static void bind(String tenantId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("🔥 TenantContext.bind chamado DENTRO de transação! tenant=" + tenantId);
        }

        String normalized = (tenantId != null ? tenantId.trim() : null);

        // "public" = null (sem tenant)
        if (!StringUtils.hasText(normalized)) {
            CurrentTenantSchemaResolver.bindTenantToCurrentThread(null);
            log.info("🔄 Tenant bindado para PUBLIC (null) | thread={}", Thread.currentThread().threadId());
            return;
        }

        CurrentTenantSchemaResolver.bindTenantToCurrentThread(normalized);
        log.info("🔄 Tenant bindado | thread={} | tenant={}", Thread.currentThread().threadId(), normalized);
    }

    /**
     * Remove qualquer tenant (equivalente a PUBLIC).
     * Prefira usar publicScope()/scope() com try-with-resources.
     */
    public static void clear() {
        CurrentTenantSchemaResolver.unbindTenantFromCurrentThread();
        log.info("🧹 Tenant desbindado | thread={}", Thread.currentThread().threadId());
    }

    // ✅ escopo seguro (restaura o tenant anterior ao sair)
    public static Scope scope(String tenantId) {
        String previous = getOrNull();
        bind(tenantId);
        return new Scope(previous);
    }

    // ✅ escopo PUBLIC explícito (restaura o tenant anterior ao sair)
    public static Scope publicScope() {
        String previous = getOrNull();
        bind(null); // explícito: public = sem tenant
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed = false;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                // restaura exatamente o que estava antes (tenant ou public)
                TenantContext.bind(previous);
                closed = true;
            }
        }
    }
}
