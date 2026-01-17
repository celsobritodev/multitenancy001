package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.application.provisioning.TenantSchemaProvisioningService;

@Component
public class TenantExecutor {

    private final TenantSchemaProvisioningService tenantSchemaService;

    public TenantExecutor(TenantSchemaProvisioningService tenantSchemaService) {
        this.tenantSchemaService = tenantSchemaService;
    }

    public <T> T run(String schemaName, Supplier<T> fn) {
        if (schemaName == null || "public".equals(schemaName)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }

        // ✅ padronizado: nada de bind/clear manual
        try (TenantContext.Scope ignored = TenantContext.scope(schemaName)) {
            return fn.get();
        }
    }

    public void run(String schemaName, Runnable fn) {
        run(schemaName, () -> { fn.run(); return null; });
    }

    /** Retorna defaultValue se schemaName/tabela não existir (bom p/ side-effects). */
    public <T> T runIfReady(String schemaName, String requiredTable, Supplier<T> fn, T defaultValue) {
        if (schemaName == null || "public".equals(schemaName)) return defaultValue;
        if (!tenantSchemaService.schemaExists(schemaName)) return defaultValue;
        if (requiredTable != null && !tenantSchemaService.tableExists(schemaName, requiredTable)) return defaultValue;
        return run(schemaName, fn);
    }

    /** Lança ApiException se schemaName/tabela não existir (bom p/ endpoints admin). */
    public void assertReadyOrThrow(String schemaName, String requiredTable) {
        if (schemaName == null || "public".equals(schemaName)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }
        if (!tenantSchemaService.schemaExists(schemaName)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "SchemaName do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaService.tableExists(schemaName, requiredTable)) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela " + requiredTable + " não existe no tenant", 404);
        }
    }

    public <T> T runOrThrow(String schemaName, String requiredTable, Supplier<T> fn) {
        assertReadyOrThrow(schemaName, requiredTable);
        return run(schemaName, fn);
    }
}
