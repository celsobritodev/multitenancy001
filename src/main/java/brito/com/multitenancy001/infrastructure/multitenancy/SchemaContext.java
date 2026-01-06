package brito.com.multitenancy001.infrastructure.multitenancy;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.multitenancy.hibernate.CurrentSchemaIdentifierResolverImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchemaContext {



    /**
     * Retorna o tenant atual (com fallback para public)
     */
    public static String getCurrentSchema() {
        // ✅ usando o método compatível com fallback
        return CurrentSchemaIdentifierResolverImpl.resolveBoundTenantOrDefault();
    }

    /**
     * Bind do tenant à thread atual.
     * ⚠️ Deve ser chamado ANTES de qualquer operação transacional.
     */
    public static void bindSchema(String tenantId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.error("🔥 ERRO GRAVE: bindTenant chamado DENTRO de transação! tenant={}", tenantId);
        }

        String normalized = (tenantId != null ? tenantId.trim() : null);

        // Se vier vazio/nulo, remove (estado real fica sem tenant)
        if (!StringUtils.hasText(normalized)) {
            CurrentSchemaIdentifierResolverImpl.bindTenantToCurrentThread(null);
            log.info("🔄 Tenant limpo (sem tenant) | thread={}", Thread.currentThread().threadId());
            return;
        }

        CurrentSchemaIdentifierResolverImpl.bindTenantToCurrentThread(normalized);

        log.info("🔄 Tenant bindado | thread={} | tenant={}",
                Thread.currentThread().threadId(),
                normalized);
    }

    /**
     * Remove o tenant da thread atual.
     * ⚠️ Deve ser chamado no finally do filtro/interceptor.
     */
    public static void unbindSchema() {
        CurrentSchemaIdentifierResolverImpl.unbindTenantFromCurrentThread();
        log.info("🧹 Tenant desbindado | thread={}", Thread.currentThread().threadId());
    }

    /**
     * (Opcional) alias pra compatibilidade, se você usar "clear()" em algum lugar.
     */
    public static void clear() {
        unbindSchema();
    }
}
