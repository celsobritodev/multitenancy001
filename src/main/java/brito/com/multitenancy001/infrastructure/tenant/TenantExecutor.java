package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

@Component
public class TenantExecutor {

    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;

    public TenantExecutor(TenantSchemaProvisioningService tenantSchemaProvisioningService) {
        this.tenantSchemaProvisioningService = tenantSchemaProvisioningService;
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

        if (s == null || s.isBlank() || Schemas.CONTROL_PLANE.equalsIgnoreCase(s)) return defaultValue;
        if (!tenantSchemaProvisioningService.schemaExists(s)) return defaultValue;
        if (requiredTable != null && !tenantSchemaProvisioningService.tableExists(s, requiredTable)) return defaultValue;

        return run(s, fn);
    }

    /**
     * Overload conveniente: defaultValue = null
     * (útil quando o caller aceita null como "não aplicou side-effect").
     */
    public <T> T runIfReady(String schemaName, String requiredTable, Supplier<T> fn) {
        return runIfReady(schemaName, requiredTable, fn, null);
    }

    /**
     * Overload para Runnable (quando o caller não precisa retornar nada).
     */
    public void runIfReady(String schemaName, String requiredTable, Runnable fn) {
        runIfReady(schemaName, requiredTable, () -> {
            fn.run();
            return null;
        }, null);
    }

    /** Lança ApiException se schemaName/tabela não existir (bom p/ endpoints admin). */
    public void assertReadyOrThrow(String schemaName, String requiredTable) {
        String s = (schemaName == null ? null : schemaName.trim());

        if (s == null || s.isBlank() || Schemas.CONTROL_PLANE.equalsIgnoreCase(s)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }
        if (!tenantSchemaProvisioningService.schemaExists(s)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "SchemaName do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaProvisioningService.tableExists(s, requiredTable)) {
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
