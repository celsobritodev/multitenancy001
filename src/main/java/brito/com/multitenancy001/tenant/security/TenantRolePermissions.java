package brito.com.multitenancy001.tenant.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import static brito.com.multitenancy001.tenant.security.TenantPermission.*;

/**
 * Centraliza a matriz Role -> Permissions do Tenant.
 *
 * Regras:
 * - sempre devolve Set imutável
 * - toda role deve estar mapeada explicitamente
 * - FAIL-FAST: role sem mapeamento explode na inicialização e/ou no uso
 */
public final class TenantRolePermissions {

    private static final EnumMap<TenantRole, Set<TenantPermission>> MAP = new EnumMap<>(TenantRole.class);

    static {
        // OWNER = tudo
        MAP.put(TenantRole.TENANT_OWNER, unmodifiable(EnumSet.allOf(TenantPermission.class)));

        // ADMIN = "admin total" do tenant (coerente com delete user + settings write + role transfer)
        MAP.put(TenantRole.TENANT_ADMIN, unmodifiable(EnumSet.of(
                // Users
                TEN_USER_READ,
                TEN_USER_CREATE,
                TEN_USER_UPDATE,
                TEN_USER_SUSPEND,
                TEN_USER_RESTORE,
                TEN_USER_DELETE,

                // Transfer ownership/admin
                TEN_ROLE_TRANSFER,

                // Products + Inventory
                TEN_PRODUCT_READ,
                TEN_PRODUCT_WRITE,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,

                // Catalog
                TEN_CATEGORY_READ,
                TEN_CATEGORY_WRITE,
                TEN_SUPPLIER_READ,
                TEN_SUPPLIER_WRITE,

                // Sales + Issues + Reports
                TEN_SALE_READ,
                TEN_SALE_WRITE,
                TEN_SALE_ISSUES_READ,
                TEN_SALE_ISSUES_WRITE,
                TEN_REPORT_SALES_READ,

                // Billing + Settings
                TEN_BILLING_READ,
                TEN_BILLING_WRITE,
                TEN_SETTINGS_READ,
                TEN_SETTINGS_WRITE
        )));

        /**
         * MANAGER = "admin operacional" (sem poderes sensíveis/destrutivos).
         * - NÃO transfere ownership/admin (TEN_ROLE_TRANSFER)
         * - NÃO deleta usuário (TEN_USER_DELETE)
         * - NÃO escreve billing/settings (TEN_BILLING_WRITE / TEN_SETTINGS_WRITE)
         */
        MAP.put(TenantRole.TENANT_MANAGER, unmodifiable(EnumSet.of(
                // Users (sem delete)
                TEN_USER_READ,
                TEN_USER_CREATE,
                TEN_USER_UPDATE,
                TEN_USER_SUSPEND,
                TEN_USER_RESTORE,

                // Products + Inventory
                TEN_PRODUCT_READ,
                TEN_PRODUCT_WRITE,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,

                // Catalog
                TEN_CATEGORY_READ,
                TEN_CATEGORY_WRITE,
                TEN_SUPPLIER_READ,
                TEN_SUPPLIER_WRITE,

                // Sales + Issues + Reports
                TEN_SALE_READ,
                TEN_SALE_WRITE,
                TEN_SALE_ISSUES_READ,
                TEN_SALE_ISSUES_WRITE,
                TEN_REPORT_SALES_READ,

                // Billing + Settings (read-only)
                TEN_BILLING_READ,
                TEN_SETTINGS_READ
        )));

        MAP.put(TenantRole.TENANT_PRODUCT_MANAGER, unmodifiable(EnumSet.of(
                TEN_PRODUCT_READ,
                TEN_PRODUCT_WRITE,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE
        )));

        MAP.put(TenantRole.TENANT_SALES_MANAGER, unmodifiable(EnumSet.of(
                TEN_SALE_READ,
                TEN_SALE_WRITE,
                TEN_SALE_ISSUES_READ,
                TEN_SALE_ISSUES_WRITE,
                TEN_REPORT_SALES_READ
        )));

        MAP.put(TenantRole.TENANT_BILLING_MANAGER, unmodifiable(EnumSet.of(
                TEN_BILLING_READ,
                TEN_BILLING_WRITE
        )));

        // READ_ONLY = auditor/consulta (sem write)
        MAP.put(TenantRole.TENANT_READ_ONLY, unmodifiable(EnumSet.of(
                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,
                TEN_CATEGORY_READ,
                TEN_SUPPLIER_READ,
                TEN_SALE_READ,
                TEN_SALE_ISSUES_READ,
                TEN_REPORT_SALES_READ,
                TEN_USER_READ,
                TEN_BILLING_READ,
                TEN_SETTINGS_READ
        )));

        // USER = usuário comum (opera o sistema)
        MAP.put(TenantRole.TENANT_USER, unmodifiable(EnumSet.of(
                TEN_PRODUCT_READ,
                TEN_CATEGORY_READ,
                TEN_SUPPLIER_READ,
                TEN_INVENTORY_READ,
                TEN_SALE_READ,
                TEN_SALE_WRITE
        )));

        // OPERATOR operacional (não administra usuários/config/billing)
        MAP.put(TenantRole.TENANT_OPERATOR, unmodifiable(EnumSet.of(
                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,
                TEN_SALE_READ
        )));

        // SUPPORT (tenant) – suporte interno sem “poder destrutivo total”
        MAP.put(TenantRole.TENANT_SUPPORT, unmodifiable(EnumSet.of(
                TEN_USER_READ,
                TEN_USER_UPDATE,
                TEN_USER_SUSPEND,
                TEN_USER_RESTORE,

                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,

                TEN_SALE_READ,
                TEN_SALE_ISSUES_READ,

                TEN_SETTINGS_READ,
                TEN_BILLING_READ
        )));

        // FAIL-FAST: garante que todas as roles do enum estão mapeadas.
        assertAllRolesMapped();
    }

    private TenantRolePermissions() {}

    public static Set<TenantPermission> permissionsFor(TenantRole role) {
        if (role == null) return Set.of();
        Set<TenantPermission> set = MAP.get(role);
        if (set == null) {
            throw new IllegalStateException("Role do Tenant sem mapeamento em TenantRolePermissions: " + role);
        }
        return set;
    }

    private static Set<TenantPermission> unmodifiable(EnumSet<TenantPermission> set) {
        return Collections.unmodifiableSet(set);
    }

    private static void assertAllRolesMapped() {
        for (TenantRole role : TenantRole.values()) {
            if (!MAP.containsKey(role)) {
                throw new IllegalStateException("Role do Tenant sem mapeamento em TenantRolePermissions: " + role);
            }
        }
    }
}
