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

    // ---------------------------------------------------------------------
    // ✅ Contexto ativo => tenantSchema
    // ---------------------------------------------------------------------

    public static void bindSchemaToCurrentThread(String tenantSchema) {
        TenantContext.bindTenantSchema(tenantSchema);
    }

    public static String resolveBoundSchemaOrNull() {
        return TenantContext.getOrNull(); // null = PUBLIC
    }

    public static String resolveBoundSchemaOrDefault() {
        String tenantSchema = TenantContext.getOrNull();
        return (tenantSchema != null ? tenantSchema : DEFAULT_SCHEMA);
    }

    public static void unbindSchemaFromCurrentThread() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------------
    // Hibernate integration
    // ---------------------------------------------------------------------

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantSchema = TenantContext.getOrNull(); // null = public
        String resolved = (StringUtils.hasText(tenantSchema) ? tenantSchema : DEFAULT_SCHEMA);

        if (log.isDebugEnabled()) {
            log.debug("🏷️ Hibernate resolveu schema={} (bound={}, default={})",
                    resolved, tenantSchema, DEFAULT_SCHEMA);
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
