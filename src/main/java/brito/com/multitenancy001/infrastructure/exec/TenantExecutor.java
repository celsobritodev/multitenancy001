package brito.com.multitenancy001.infrastructure.exec;

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

    public <T> T run(String schema, Supplier<T> fn) {
        if (schema == null || "public".equals(schema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }

        // ✅ padronizado: nada de bind/clear manual
        try (TenantContext.Scope ignored = TenantContext.scope(schema)) {
            return fn.get();
        }
    }

    public void run(String schema, Runnable fn) {
        run(schema, () -> { fn.run(); return null; });
    }

    /** Retorna defaultValue se schema/tabela não existir (bom p/ side-effects). */
    public <T> T runIfReady(String schema, String requiredTable, Supplier<T> fn, T defaultValue) {
        if (schema == null || "public".equals(schema)) return defaultValue;
        if (!tenantSchemaService.schemaExists(schema)) return defaultValue;
        if (requiredTable != null && !tenantSchemaService.tableExists(schema, requiredTable)) return defaultValue;
        return run(schema, fn);
    }

    /** Lança ApiException se schema/tabela não existir (bom p/ endpoints admin). */
    public void assertReadyOrThrow(String schema, String requiredTable) {
        if (schema == null || "public".equals(schema)) {
            throw new ApiException("TENANT_INVALID", "Tenant inválido", 404);
        }
        if (!tenantSchemaService.schemaExists(schema)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaService.tableExists(schema, requiredTable)) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela " + requiredTable + " não existe no tenant", 404);
        }
    }

    public <T> T runOrThrow(String schema, String requiredTable, Supplier<T> fn) {
        assertReadyOrThrow(schema, requiredTable);
        return run(schema, fn);
    }
}
