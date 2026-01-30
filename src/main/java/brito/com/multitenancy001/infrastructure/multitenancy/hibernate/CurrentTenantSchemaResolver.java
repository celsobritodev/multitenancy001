package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;

@Slf4j
@Component
public class CurrentTenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    /**
     * ✅ Compatibilidade: mantém os mesmos métodos estáticos que seu código já usa.
     * Agora eles delegam ao TenantContext (fonte única).
     */

    public static void bindTenantToCurrentThread(String tenantId) {
        TenantContext.bind(tenantId);
    }

    public static String resolveBoundTenantOrNull() {
        // TenantContext.getOrNull() já devolve null quando está em PUBLIC
        return TenantContext.getOrNull();
    }

    public static String resolveBoundTenantOrDefault() {
        String t = TenantContext.getOrNull();
        return (t != null ? t : DEFAULT_SCHEMA);
    }

    public static void unbindTenantFromCurrentThread() {
        TenantContext.clear();
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getOrNull(); // null = public
        String resolved = (StringUtils.hasText(tenant) ? tenant : DEFAULT_SCHEMA);

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
