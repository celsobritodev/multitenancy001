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
    // ✅ NOVO PADRÃO (limpo, coerente)
    // ---------------------------------------------------------------------

    public static void bindSchemaToCurrentThread(String tenantSchema) {
        TenantContext.bindTenantSchema(tenantSchema);
    }

    public static String resolveBoundSchemaOrNull() {
        return TenantContext.getOrNull(); // null = PUBLIC
    }

    public static String resolveBoundSchemaOrDefault() {
        String schema = TenantContext.getOrNull();
        return (schema != null ? schema : DEFAULT_SCHEMA);
    }

    public static void unbindSchemaFromCurrentThread() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------------
    // ⚠️ COMPATIBILIDADE (NÃO USAR EM CÓDIGO NOVO)
    // ---------------------------------------------------------------------

    /**
     * @deprecated use {@link #bindSchemaToCurrentThread(String)}.
     */
    @Deprecated
    public static void bindTenantSchemaToCurrentThread(String tenantSchema) {
        bindSchemaToCurrentThread(tenantSchema);
    }

    /**
     * @deprecated use {@link #bindSchemaToCurrentThread(String)}.
     */
    @Deprecated
    public static void bindTenantToCurrentThread(String tenantId) {
        bindSchemaToCurrentThread(tenantId);
    }

    /**
     * @deprecated use {@link #resolveBoundSchemaOrNull()}.
     */
    @Deprecated
    public static String resolveBoundTenantSchemaOrNull() {
        return resolveBoundSchemaOrNull();
    }

    /**
     * @deprecated use {@link #resolveBoundSchemaOrNull()}.
     */
    @Deprecated
    public static String resolveBoundTenantOrNull() {
        return resolveBoundSchemaOrNull();
    }

    /**
     * @deprecated use {@link #resolveBoundSchemaOrDefault()}.
     */
    @Deprecated
    public static String resolveBoundTenantSchemaOrDefault() {
        return resolveBoundSchemaOrDefault();
    }

    /**
     * @deprecated use {@link #resolveBoundSchemaOrDefault()}.
     */
    @Deprecated
    public static String resolveBoundTenantOrDefault() {
        return resolveBoundSchemaOrDefault();
    }

    /**
     * @deprecated use {@link #unbindSchemaFromCurrentThread()}.
     */
    @Deprecated
    public static void unbindTenantSchemaFromCurrentThread() {
        unbindSchemaFromCurrentThread();
    }

    /**
     * @deprecated use {@link #unbindSchemaFromCurrentThread()}.
     */
    @Deprecated
    public static void unbindTenantFromCurrentThread() {
        unbindSchemaFromCurrentThread();
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

