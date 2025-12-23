package brito.com.multitenancy001.configuration;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {



    /**
     * Retorna o tenant atual (com fallback para public)
     */
    public static String getCurrentTenant() {
        // ✅ usando o método compatível com fallback
        return CurrentTenantIdentifierResolverImpl.resolveBoundTenantOrDefault();
    }

    /**
     * Bind do tenant à thread atual.
     * ⚠️ Deve ser chamado ANTES de qualquer operação transacional.
     */
    public static void bindTenant(String tenantId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.error("🔥 ERRO GRAVE: bindTenant chamado DENTRO de transação! tenant={}", tenantId);
        }

        String normalized = (tenantId != null ? tenantId.trim() : null);

        // Se vier vazio/nulo, remove (estado real fica sem tenant)
        if (!StringUtils.hasText(normalized)) {
            CurrentTenantIdentifierResolverImpl.bindTenantToCurrentThread(null);
            log.info("🔄 Tenant limpo (sem tenant) | thread={}", Thread.currentThread().threadId());
            return;
        }

        CurrentTenantIdentifierResolverImpl.bindTenantToCurrentThread(normalized);

        log.info("🔄 Tenant bindado | thread={} | tenant={}",
                Thread.currentThread().threadId(),
                normalized);
    }

    /**
     * Remove o tenant da thread atual.
     * ⚠️ Deve ser chamado no finally do filtro/interceptor.
     */
    public static void unbindTenant() {
        CurrentTenantIdentifierResolverImpl.unbindTenantFromCurrentThread();
        log.info("🧹 Tenant desbindado | thread={}", Thread.currentThread().threadId());
    }

    /**
     * (Opcional) alias pra compatibilidade, se você usar "clear()" em algum lugar.
     */
    public static void clear() {
        unbindTenant();
    }
}
