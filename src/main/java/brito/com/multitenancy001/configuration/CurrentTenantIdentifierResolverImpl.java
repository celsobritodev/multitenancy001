package brito.com.multitenancy001.configuration;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class CurrentTenantIdentifierResolverImpl
        implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";
    private static final ThreadLocal<String> TENANT_THREAD_LOCAL = new ThreadLocal<>();

    public static void bindTenantToCurrentThread(String tenantId) {
        String previous = TENANT_THREAD_LOCAL.get();

        if (StringUtils.hasText(tenantId)) {
            TENANT_THREAD_LOCAL.set(tenantId);
            log.info("🔄 Tenant bindado à thread: {} -> {}", previous, tenantId);
        } else {
            TENANT_THREAD_LOCAL.remove();
            log.info("🧹 Tenant removido da thread (anterior: {})", previous);
        }
    }

    public static String resolveBoundTenant() {
        return TENANT_THREAD_LOCAL.get() != null
                ? TENANT_THREAD_LOCAL.get()
                : DEFAULT_TENANT;
    }

    public static void unbindTenantFromCurrentThread() {
        String previous = TENANT_THREAD_LOCAL.get();
        TENANT_THREAD_LOCAL.remove();
        log.info("🧹 Tenant desbindado da thread (anterior: {})", previous);
    }
    
    
    
    
    @Override
    public String resolveCurrentTenantIdentifier() {
        return resolveBoundTenant();
    }
    
    

    @Override
    public boolean validateExistingCurrentSessions() {
        // 🔥 ESSENCIAL
        return true;
    }

    @Override
    public boolean isRoot(String tenantIdentifier) {
        return DEFAULT_TENANT.equals(tenantIdentifier);
    }
}
