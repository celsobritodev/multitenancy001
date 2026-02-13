package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

@Component
public class TenantExecutor {

    /**
     * Worker de "readiness" / provisioning introspection.
     *
     * IMPORTANTE:
     * - Este executor NÃO cria schema nem roda migrations.
     * - Ele só troca contexto (TenantContext) e checa existência.
     */
    private final TenantSchemaProvisioningWorker tenantSchemaProvisioningWorker;

    public TenantExecutor(TenantSchemaProvisioningWorker tenantSchemaProvisioningWorker) {
        this.tenantSchemaProvisioningWorker = tenantSchemaProvisioningWorker;
    }

    // ---------------------------------------------------------------------
    // Execução no tenant (assume tenantSchema informado e válido)
    // ---------------------------------------------------------------------

    public <T> T runInTenantSchema(String tenantSchema, Supplier<T> fn) {
        String normalized = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalized == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalized)) {
            throw new ApiException(ApiErrorCode.TENANT_INVALID, "Tenant inválido");
        }
        if (fn == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "fn é obrigatório");
        }

        try (TenantContext.Scope ignored = TenantContext.scope(normalized)) {
            return fn.get();
        }
    }

    /**
     * Executa somente se o tenant estiver pronto.
     *
     * - Se schema não existe => retorna defaultValue.
     * - Se requiredTable != null e tabela não existe => retorna defaultValue.
     */
    public <T> T runInTenantSchemaIfReady(
            String tenantSchema,
            String requiredTable,
            Supplier<T> fn,
            T defaultValue
    ) {
        String normalized = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalized == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalized)) return defaultValue;
        if (fn == null) return defaultValue;

        if (!tenantSchemaProvisioningWorker.schemaExists(normalized)) return defaultValue;

        if (requiredTable != null && !requiredTable.isBlank()) {
            boolean ok = tenantSchemaProvisioningWorker.tableExists(normalized, requiredTable.trim());
            if (!ok) return defaultValue;
        }

        return runInTenantSchema(normalized, fn);
    }

    public <T> T runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return runInTenantSchemaIfReady(tenantSchema, requiredTable, fn, null);
    }

    // ---------------------------------------------------------------------
    // Assert ready / OrThrow
    // ---------------------------------------------------------------------

    public void assertTenantSchemaReadyOrThrow(String tenantSchema, String requiredTable) {
        String normalized = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalized == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalized)) {
            throw new ApiException(ApiErrorCode.TENANT_INVALID, "Tenant inválido");
        }

        if (!tenantSchemaProvisioningWorker.schemaExists(normalized)) {
            throw new ApiException(ApiErrorCode.TENANT_SCHEMA_NOT_FOUND, "Schema do tenant não existe: " + normalized);
        }

        if (requiredTable != null && !requiredTable.isBlank()) {
            boolean ok = tenantSchemaProvisioningWorker.tableExists(normalized, requiredTable.trim());
            if (!ok) {
                throw new ApiException(
                        ApiErrorCode.TENANT_TABLE_NOT_FOUND,
                        "Tabela " + requiredTable.trim() + " não existe no tenant " + normalized
                );
            }
        }
    }

    public <T> T runInTenantSchemaOrThrow(String tenantSchema, String requiredTable, Supplier<T> fn) {
        if (fn == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "fn é obrigatório");
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
