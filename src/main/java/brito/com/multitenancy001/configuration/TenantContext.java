package brito.com.multitenancy001.configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    /**
     * Retorna o tenant atualmente bindado à thread
     */
    public static String getCurrentTenant() {
        return CurrentTenantIdentifierResolverImpl.resolveBoundTenant();
    }

    /**
     * Bind do tenant à thread atual.
     * ⚠️ Deve ser chamado ANTES de qualquer operação transacional.
     */
    public static void bindTenant(String tenantId) {
        CurrentTenantIdentifierResolverImpl.bindTenantToCurrentThread(tenantId);
    }

    /**
     * Remove o tenant da thread atual.
     * ⚠️ Deve ser chamado no finally do filtro/interceptor.
     */
    public static void unbindTenant() {
        CurrentTenantIdentifierResolverImpl.unbindTenantFromCurrentThread();
    }
}
