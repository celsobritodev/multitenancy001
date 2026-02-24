package brito.com.multitenancy001.infrastructure.tenant;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executor central do contexto de Tenant.
 *
 * <p>Responsabilidade: garantir que o {@link TenantContext} esteja corretamente bindado
 * para um schema de tenant válido durante a execução de um bloco.</p>
 *
 * <p>Esse bind é o que permite que o Hibernate multi-tenant por schema escolha
 * o schema correto (ex.: via CurrentTenantIdentifierResolver / MultiTenantConnectionProvider).</p>
 */
@Component
public class TenantExecutor {

    private static final Logger log = LoggerFactory.getLogger(TenantExecutor.class);

    private final TenantSchemaProvisioningWorker tenantSchemaProvisioningWorker;

    public TenantExecutor(TenantSchemaProvisioningWorker tenantSchemaProvisioningWorker) {
        this.tenantSchemaProvisioningWorker = tenantSchemaProvisioningWorker;
    }

    // ---------------------------------------------------------------------
    // Execução: tenant pronto => sempre tenantSchema
    // ---------------------------------------------------------------------

    public <T> T runInTenantSchema(String tenantSchema, Supplier<T> fn) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_INVALID, "Tenant inválido", 404);
        }

        if (log.isDebugEnabled()) {
            log.debug("[TENANT] bind -> schema={}", normalizedTenantSchema);
        }

        try (TenantContext.Scope ignored = TenantContext.scope(normalizedTenantSchema)) {
            return fn.get();
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("[TENANT] unbind -> back to PUBLIC");
            }
        }
    }

    public void runInTenantSchema(String tenantSchema, Runnable fn) {
        runInTenantSchema(tenantSchema, () -> {
            fn.run();
            return null;
        });
    }

    /**
     * Executa apenas se schema e tabela existirem; caso contrário retorna {@code defaultValue}.
     *
     * <p>Útil para rotinas que podem rodar antes do provisionamento completo do tenant.</p>
     */
    public <T> T runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn, T defaultValue) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) return defaultValue;
        if (!tenantSchemaProvisioningWorker.schemaExists(normalizedTenantSchema)) return defaultValue;
        if (requiredTable != null && !tenantSchemaProvisioningWorker.tableExists(normalizedTenantSchema, requiredTable)) return defaultValue;

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

    /**
     * Valida que schema (e opcionalmente tabela) existe; caso contrário lança {@link ApiException}.
     */
    public void assertTenantSchemaReadyOrThrow(String tenantSchema, String requiredTable) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_INVALID, "Tenant inválido", 404);
        }
        if (!tenantSchemaProvisioningWorker.schemaExists(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_SCHEMA_NOT_FOUND, "Schema do tenant não existe", 404);
        }
        if (requiredTable != null && !tenantSchemaProvisioningWorker.tableExists(normalizedTenantSchema, requiredTable)) {
            throw new ApiException(ApiErrorCode.TENANT_TABLE_NOT_FOUND, "Tabela " + requiredTable + " não existe no tenant", 404);
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