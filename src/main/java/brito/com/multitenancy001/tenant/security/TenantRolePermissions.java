package brito.com.multitenancy001.tenant.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import static brito.com.multitenancy001.tenant.security.TenantPermission.*;

public final class TenantRolePermissions {

    private static final EnumMap<TenantRole, Set<TenantPermission>> MAP = new EnumMap<>(TenantRole.class);

    static {
        // OWNER = tudo
        MAP.put(TenantRole.TENANT_OWNER, unmodifiable(EnumSet.allOf(TenantPermission.class)));

        // ADMIN = administra tudo do tenant (conforme seu set de TenantPermission)
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
                TEN_SALE_ISSUES_WRITE,   // ✅ FIX: seu enum tem WRITE; admin normalmente deve ter
                TEN_REPORT_SALES_READ,

                // Billing + Settings
                TEN_BILLING_READ,
                TEN_BILLING_WRITE,
                TEN_SETTINGS_READ,
                TEN_SETTINGS_WRITE
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
                TEN_SALE_ISSUES_WRITE,   // ✅ coerente com SALES_MANAGER também (remova se não quiser)
                TEN_REPORT_SALES_READ
        )));

        MAP.put(TenantRole.TENANT_BILLING_MANAGER, unmodifiable(EnumSet.of(
                TEN_BILLING_READ,
                TEN_BILLING_WRITE
        )));

        MAP.put(TenantRole.TENANT_READ_ONLY, unmodifiable(EnumSet.of(
                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,
                TEN_USER_READ
        )));

        // OPERATOR operacional (não administra usuários/config/billing)
        MAP.put(TenantRole.TENANT_OPERATOR, unmodifiable(EnumSet.of(
                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,
                TEN_SALE_READ
        )));
    }

    private TenantRolePermissions() {}

    /**
     * Mantém o mesmo nome do seu método atual.
     * Retorna Set imutável (consistente com CP).
     */
    public static Set<TenantPermission> permissionsFor(TenantRole role) {
        if (role == null) return Set.of();
        return MAP.getOrDefault(role, Set.of());
    }

    private static Set<TenantPermission> unmodifiable(EnumSet<TenantPermission> set) {
        return Collections.unmodifiableSet(set);
    }
}
