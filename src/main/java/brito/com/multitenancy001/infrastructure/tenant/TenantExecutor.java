package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;

@Component
public class TenantExecutor {

    private final TenantSchemaProvisioningService tenantSchemaService;

    public TenantExecutor(TenantSchemaProvisioningService tenantSchemaService) {
        this.tenantSchemaService = tenantSchemaService;
    }

    public <T> T run(String schemaName, Supplier<T> fn) {
        String theSchema = (schemaName == null ? null : schemaName.trim());

        if (theSchema == null || theSchema.isBlank() || Schemas.CONTROL_PLANE.equalsIgnoreCase(theSchema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }

        // ✅ padronizado: nada de bind/clear manual
        try (TenantContext.Scope ignored = TenantContext.scope(theSchema)) {
            return fn.get();
        }
    }

    public void run(String schemaName, Runnable fn) {
        run(schemaName, () -> {
            fn.run();
            return null;
        });
    }

    /** Retorna defaultValue se schemaName/tabela não existir (bom p/ side-effects). */
    public <T> T runIfReady(String schemaName, String requiredTable, Supplier<T> fn, T defaultValue) {
        String s = (schemaName == null ? null : schemaName.trim());
        if (s == null || s.isBlank() ||Schemas.CONTROL_PLANE.equalsIgnoreCase(s)) return defaultValue;
        if (!tenantSchemaService.schemaExists(s)) return defaultValue;
        if (requiredTable != null && !tenantSchemaService.tableExists(s, requiredTable)) return defaultValue;
        return run(s, fn);
    }

    /** Lança ApiException se schemaName/tabela não existir (bom p/ endpoints admin). */
    public void assertReadyOrThrow(String schemaName, String requiredTable) {
        String s = (schemaName == null ? null : schemaName.trim());

        if (s == null || s.isBlank() ||Schemas.CONTROL_PLANE.equalsIgnoreCase(s)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }
        if (!tenantSchemaService.schemaExists(s)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "SchemaName do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaService.tableExists(s, requiredTable)) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela " + requiredTable + " não existe no tenant", 404);
        }
    }

    public <T> T runOrThrow(String schemaName, String requiredTable, Supplier<T> fn) {
        assertReadyOrThrow(schemaName, requiredTable);
        return run(schemaName, fn);
    }

    // overload conveniente
    public <T> T runOrThrow(String schemaName, Supplier<T> fn) {
        return runOrThrow(schemaName, null, fn);
    }
}
