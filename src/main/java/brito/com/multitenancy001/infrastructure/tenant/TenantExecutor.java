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

    // ---------------------------------------------------------------------
    // ✅ NOVO PADRÃO (semântico): "schema" / "tenantSchema"
    // ---------------------------------------------------------------------

    public <T> T runInSchema(String tenantSchema, Supplier<T> fn) {
        return run(tenantSchema, fn);
    }

    public void runInSchema(String tenantSchema, Runnable fn) {
        run(tenantSchema, fn);
    }

    public <T> T runInSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn, T defaultValue) {
        return runIfReady(tenantSchema, requiredTable, fn, defaultValue);
    }

    public <T> T runInSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return runIfReady(tenantSchema, requiredTable, fn, null);
    }

    public void runInSchemaIfReady(String tenantSchema, String requiredTable, Runnable fn) {
        runIfReady(tenantSchema, requiredTable, fn);
    }

    public void assertSchemaReadyOrThrow(String tenantSchema, String requiredTable) {
        assertReadyOrThrow(tenantSchema, requiredTable);
    }

    public <T> T runInSchemaOrThrow(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return runOrThrow(tenantSchema, requiredTable, fn);
    }

    public <T> T runInSchemaOrThrow(String tenantSchema, Supplier<T> fn) {
        return runOrThrow(tenantSchema, null, fn);
    }

    // ---------------------------------------------------------------------
    // ✅ API ATUAL (mantida): schemaName (compat)
    // ---------------------------------------------------------------------

    public <T> T run(String schemaName, Supplier<T> fn) {
        String tenantSchema = normalizeTenantSchemaOrNull(schemaName);

        if (tenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }

        try (TenantContext.Scope ignored = TenantContext.scope(tenantSchema)) {
            return fn.get();
        }
    }

    public void run(String schemaName, Runnable fn) {
        run(schemaName, () -> {
            fn.run();
            return null;
        });
    }

    /** Retorna defaultValue se tenantSchema/tabela não existir. */
    public <T> T runIfReady(String schemaName, String requiredTable, Supplier<T> fn, T defaultValue) {
        String tenantSchema = normalizeTenantSchemaOrNull(schemaName);

        if (tenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) return defaultValue;
        if (!tenantSchemaProvisioningService.schemaExists(tenantSchema)) return defaultValue;
        if (requiredTable != null && !tenantSchemaProvisioningService.tableExists(tenantSchema, requiredTable)) return defaultValue;

        return run(tenantSchema, fn);
    }

    public <T> T runIfReady(String schemaName, String requiredTable, Supplier<T> fn) {
        return runIfReady(schemaName, requiredTable, fn, null);
    }

    public void runIfReady(String schemaName, String requiredTable, Runnable fn) {
        runIfReady(schemaName, requiredTable, () -> {
            fn.run();
            return null;
        }, null);
    }

    /** Lança ApiException se tenantSchema/tabela não existir. */
    public void assertReadyOrThrow(String schemaName, String requiredTable) {
        String tenantSchema = normalizeTenantSchemaOrNull(schemaName);

        if (tenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }
        if (!tenantSchemaProvisioningService.schemaExists(tenantSchema)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "SchemaName do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaProvisioningService.tableExists(tenantSchema, requiredTable)) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela " + requiredTable + " não existe no tenant", 404);
        }
    }

    public <T> T runOrThrow(String schemaName, String requiredTable, Supplier<T> fn) {
        assertReadyOrThrow(schemaName, requiredTable);
        return run(schemaName, fn);
    }

    public <T> T runOrThrow(String schemaName, Supplier<T> fn) {
        return runOrThrow(schemaName, null, fn);
    }

    private static String normalizeTenantSchemaOrNull(String schemaName) {
        String s = (schemaName == null ? null : schemaName.trim());
        return (s == null || s.isBlank()) ? null : s;
    }
}
