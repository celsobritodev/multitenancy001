package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Helpers prontos para executar código no tenant
 * SOMENTE quando o schema + tabelas mínimas existirem.
 *
 * ✅ Evita passar string de tabela na mão (usa TenantRequiredTables)
 * ✅ Usa o TenantExecutor por baixo (sem duplicar regra)
 *
 * Padrão semântico:
 * - runInSchemaIfReady / runInSchemaOrThrow / assertSchemaReadyOrThrow
 */
@Component
@RequiredArgsConstructor
public class TenantSchemaReadinessService {

    private final TenantExecutor tenantExecutor;

    // ---------------------------------------------------------------------
    // USERS
    // ---------------------------------------------------------------------

    public <T> T runIfUsersReady(String tenantSchema, Supplier<T> supplier, T defaultValue) {
        return tenantExecutor.runInTenantSchemaIfReady(tenantSchema, TenantRequiredTables.TENANT_USERS, supplier, defaultValue);
    }

    public <T> T runIfUsersReady(String tenantSchema, Supplier<T> supplier) {
        return tenantExecutor.runInTenantSchemaIfReady(tenantSchema, TenantRequiredTables.TENANT_USERS, supplier);
    }

    public void runIfUsersReady(String tenantSchema, Runnable runnable) {
        tenantExecutor.runInTenantSchemaIfReady(tenantSchema, TenantRequiredTables.TENANT_USERS, runnable);
    }

    public void assertUsersReadyOrThrow(String tenantSchema) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, TenantRequiredTables.TENANT_USERS);
    }

    public <T> T runUsersOrThrow(String tenantSchema, Supplier<T> supplier) {
        return tenantExecutor.runInTenantSchemaOrThrow(tenantSchema, TenantRequiredTables.TENANT_USERS, supplier);
    }

    public void runUsersOrThrow(String tenantSchema, Runnable runnable) {
        tenantExecutor.runInTenantSchemaOrThrow(tenantSchema, TenantRequiredTables.TENANT_USERS, () -> {
        	runnable.run();
            return null;
        });
    }

    // ---------------------------------------------------------------------
    // PRODUCTS
    // ---------------------------------------------------------------------

    public <T> T runIfProductsReady(String tenantSchema, Supplier<T> fn, T defaultValue) {
        return tenantExecutor.runInTenantSchemaIfReady(tenantSchema, TenantRequiredTables.PRODUCTS, fn, defaultValue);
    }

    public <T> T runIfProductsReady(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchemaIfReady(tenantSchema, TenantRequiredTables.PRODUCTS, fn);
    }

    public void runIfProductsReady(String tenantSchema, Runnable fn) {
        tenantExecutor.runInTenantSchemaIfReady(tenantSchema, TenantRequiredTables.PRODUCTS, fn);
    }

    public void assertProductsReadyOrThrow(String tenantSchema) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, TenantRequiredTables.PRODUCTS);
    }

    public <T> T runProductsOrThrow(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchemaOrThrow(tenantSchema, TenantRequiredTables.PRODUCTS, fn);
    }

    public void runProductsOrThrow(String tenantSchema, Runnable fn) {
        tenantExecutor.runInTenantSchemaOrThrow(tenantSchema, TenantRequiredTables.PRODUCTS, () -> {
            fn.run();
            return null;
        });
    }

    // ---------------------------------------------------------------------
    // GENÉRICO (se quiser usar com outras tabelas do TenantRequiredTables)
    // ---------------------------------------------------------------------

    public <T> T runIfReady(String tenantSchema, String requiredTable, Supplier<T> fn, T defaultValue) {
        return tenantExecutor.runInTenantSchemaIfReady(tenantSchema, requiredTable, fn, defaultValue);
    }

    public <T> T runOrThrow(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchemaOrThrow(tenantSchema, requiredTable, fn);
    }

    public void assertReadyOrThrow(String tenantSchema, String requiredTable) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, requiredTable);
    }
}
