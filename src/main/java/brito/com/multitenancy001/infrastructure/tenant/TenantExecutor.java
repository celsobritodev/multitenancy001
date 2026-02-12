package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

@Component
public class TenantExecutor {

    private final TenantSchemaProvisioningWorker tenantSchemaProvisioningService;

    public TenantExecutor(TenantSchemaProvisioningWorker tenantSchemaProvisioningService) {
        this.tenantSchemaProvisioningService = tenantSchemaProvisioningService;
    }

    // ---------------------------------------------------------------------
    // ✅ Execução (tenant pronto) => sempre tenantSchema
    // ---------------------------------------------------------------------

    public <T> T runInTenantSchema(String tenantSchema, Supplier<T> fn) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }

        try (TenantContext.Scope ignored = TenantContext.scope(normalizedTenantSchema)) {
            return fn.get();
        }
    }

    public void runInTenantSchema(String tenantSchema, Runnable fn) {
        runInTenantSchema(tenantSchema, () -> {
            fn.run();
            return null;
        });
    }

    /** Retorna defaultValue se schema/tabela não existir. */
    public <T> T runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn, T defaultValue) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) return defaultValue;
        if (!tenantSchemaProvisioningService.schemaExists(normalizedTenantSchema)) return defaultValue;
        if (requiredTable != null && !tenantSchemaProvisioningService.tableExists(normalizedTenantSchema, requiredTable)) return defaultValue;

        return runInTenantSchema(normalizedTenantSchema, fn);
    }

    public <T> T runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return runInTenantSchemaIfReady(tenantSchema, requiredTable, fn, null);
    }

    public void runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Runnable fn) {
        runInTenantSchemaIfReady(tenantSchema, requiredTable, () -> {
            fn.run();
            return null;
        }, null);
    }

    /** Lança ApiException se schema/tabela não existir. */
    public void assertTenantSchemaReadyOrThrow(String tenantSchema, String requiredTable) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }
        if (!tenantSchemaProvisioningService.schemaExists(normalizedTenantSchema)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaProvisioningService.tableExists(normalizedTenantSchema, requiredTable)) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela " + requiredTable + " não existe no tenant", 404);
        }
    }

    public <T> T runInTenantSchemaOrThrow(String tenantSchema, String requiredTable, Supplier<T> fn) {
        assertTenantSchemaReadyOrThrow(tenantSchema, requiredTable);
        return runInTenantSchema(tenantSchema, fn);
    }

    public <T> T runInTenantSchemaOrThrow(String tenantSchema, Supplier<T> fn) {
        return runInTenantSchemaOrThrow(tenantSchema, null, fn);
    }

    private static String normalizeTenantSchemaOrNull(String tenantSchema) {
        String s = (tenantSchema == null ? null : tenantSchema.trim());
        return (s == null || s.isBlank()) ? null : s;
    }
}
