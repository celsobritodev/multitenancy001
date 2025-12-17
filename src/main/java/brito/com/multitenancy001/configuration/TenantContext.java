package brito.com.multitenancy001.configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {
    
    // DELEGA para o CurrentTenantIdentifierResolverImpl
    public static String getCurrentTenant() {
        return CurrentTenantIdentifierResolverImpl.getCurrentTenant();
    }

    public static void setCurrentTenant(String tenantId) {
        CurrentTenantIdentifierResolverImpl.setCurrentTenant(tenantId);
    }

    public static void clear() {
        CurrentTenantIdentifierResolverImpl.clear();
    }
}