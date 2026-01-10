package brito.com.multitenancy001.shared.context;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.multitenancy.hibernate.CurrentTenantSchemaResolver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    public static String getOrNull() {
        return CurrentTenantSchemaResolver.resolveBoundTenantOrDefault();
    }

    public static void bind(String tenantId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.error("🔥 ERRO GRAVE: bindTenant chamado DENTRO de transação! tenant={}", tenantId);
        }

        String normalized = (tenantId != null ? tenantId.trim() : null);

        if (!StringUtils.hasText(normalized)) {
            CurrentTenantSchemaResolver.bindTenantToCurrentThread(null);
            log.info("🔄 Tenant limpo (sem tenant) | thread={}", Thread.currentThread().threadId());
            return;
        }

        CurrentTenantSchemaResolver.bindTenantToCurrentThread(normalized);
        log.info("🔄 Tenant bindado | thread={} | tenant={}",
                Thread.currentThread().threadId(),
                normalized);
    }

    public static void clear() {
        CurrentTenantSchemaResolver.unbindTenantFromCurrentThread();
        log.info("🧹 Tenant desbindado | thread={}", Thread.currentThread().threadId());
    }

    // ✅ NOVO: escopo seguro
    public static Scope scope(String tenantId) {
        bind(tenantId);
        return new Scope();
    }

    // ✅ NOVO: escopo PUBLIC explícito (garante que não ficou tenant pendurado)
    public static Scope publicScope() {
        clear();
        return new Scope();
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed = false;

        private Scope() {}

        @Override
        public void close() {
            if (!closed) {
                TenantContext.clear();
                closed = true;
            }
        }
    }
}
