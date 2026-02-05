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
 */
@Component
@RequiredArgsConstructor
public class TenantReadyExecutor {

    private final TenantExecutor tenantExecutor;

    // ---------------------------------------------------------------------
    // USERS
    // ---------------------------------------------------------------------

    public <T> T runIfUsersReady(String tenantSchema, Supplier<T> fn, T defaultValue) {
        return tenantExecutor.runIfReady(tenantSchema, TenantRequiredTables.TENANT_USERS, fn, defaultValue);
    }

    public <T> T runIfUsersReady(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runIfReady(tenantSchema, TenantRequiredTables.TENANT_USERS, fn, null);
    }

    public void runIfUsersReady(String tenantSchema, Runnable fn) {
        tenantExecutor.runIfReady(tenantSchema, TenantRequiredTables.TENANT_USERS, fn);
    }

    public void assertUsersReadyOrThrow(String tenantSchema) {
        tenantExecutor.assertReadyOrThrow(tenantSchema, TenantRequiredTables.TENANT_USERS);
    }

    public <T> T runUsersOrThrow(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runOrThrow(tenantSchema, TenantRequiredTables.TENANT_USERS, fn);
    }

    public void runUsersOrThrow(String tenantSchema, Runnable fn) {
        tenantExecutor.runOrThrow(tenantSchema, TenantRequiredTables.TENANT_USERS, () -> {
            fn.run();
            return null;
        });
    }

    // ---------------------------------------------------------------------
    // PRODUCTS
    // ---------------------------------------------------------------------

    public <T> T runIfProductsReady(String tenantSchema, Supplier<T> fn, T defaultValue) {
        return tenantExecutor.runIfReady(tenantSchema, TenantRequiredTables.PRODUCTS, fn, defaultValue);
    }

    public <T> T runIfProductsReady(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runIfReady(tenantSchema, TenantRequiredTables.PRODUCTS, fn, null);
    }

    public void runIfProductsReady(String tenantSchema, Runnable fn) {
        tenantExecutor.runIfReady(tenantSchema, TenantRequiredTables.PRODUCTS, fn);
    }

    public void assertProductsReadyOrThrow(String tenantSchema) {
        tenantExecutor.assertReadyOrThrow(tenantSchema, TenantRequiredTables.PRODUCTS);
    }

    public <T> T runProductsOrThrow(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runOrThrow(tenantSchema, TenantRequiredTables.PRODUCTS, fn);
    }

    public void runProductsOrThrow(String tenantSchema, Runnable fn) {
        tenantExecutor.runOrThrow(tenantSchema, TenantRequiredTables.PRODUCTS, () -> {
            fn.run();
            return null;
        });
    }

    // ---------------------------------------------------------------------
    // GENÉRICO (se quiser usar com outras tabelas do TenantRequiredTables)
    // ---------------------------------------------------------------------

    public <T> T runIfReady(String tenantSchema, String requiredTable, Supplier<T> fn, T defaultValue) {
        return tenantExecutor.runIfReady(tenantSchema, requiredTable, fn, defaultValue);
    }

    public <T> T runOrThrow(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return tenantExecutor.runOrThrow(tenantSchema, requiredTable, fn);
    }

    public void assertReadyOrThrow(String tenantSchema, String requiredTable) {
        tenantExecutor.assertReadyOrThrow(tenantSchema, requiredTable);
    }
}
