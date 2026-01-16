package brito.com.multitenancy001.tenant.security;

import java.util.EnumSet;
import java.util.Set;

public final class TenantRolePermissions {

    private TenantRolePermissions() {}

    public static Set<TenantPermission> permissionsFor(TenantRole role) {
        if (role == null) return Set.of();

        return switch (role) {
            case TENANT_OWNER -> EnumSet.allOf(TenantPermission.class);

            // ADMIN = “administra tudo” do tenant (conforme seu set de TenantPermission)
            case TENANT_ADMIN -> EnumSet.of(
                    // Users
                    TenantPermission.TEN_USER_READ,
                    TenantPermission.TEN_USER_CREATE,
                    TenantPermission.TEN_USER_UPDATE,
                    TenantPermission.TEN_USER_SUSPEND,
                    TenantPermission.TEN_USER_RESTORE,
                    TenantPermission.TEN_USER_DELETE,

                    // Transfer ownership/admin
                    TenantPermission.TEN_ROLE_TRANSFER,

                    // Products + Inventory
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_PRODUCT_WRITE,
                    TenantPermission.TEN_INVENTORY_READ,
                    TenantPermission.TEN_INVENTORY_WRITE,

                    // Catalog
                    TenantPermission.TEN_CATEGORY_READ,
                    TenantPermission.TEN_CATEGORY_WRITE,
                    TenantPermission.TEN_SUPPLIER_READ,
                    TenantPermission.TEN_SUPPLIER_WRITE,

                    // Sales + Reports
                    TenantPermission.TEN_SALE_READ,
                    TenantPermission.TEN_SALE_WRITE,
                    TenantPermission.TEN_SALE_ISSUES_READ,
                    TenantPermission.TEN_REPORT_SALES_READ,

                    // Billing + Settings
                    TenantPermission.TEN_BILLING_READ,
                    TenantPermission.TEN_BILLING_WRITE,
                    TenantPermission.TEN_SETTINGS_READ,
                    TenantPermission.TEN_SETTINGS_WRITE
            );

            case TENANT_PRODUCT_MANAGER -> EnumSet.of(
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_PRODUCT_WRITE,
                    TenantPermission.TEN_INVENTORY_READ,
                    TenantPermission.TEN_INVENTORY_WRITE
            );

            case TENANT_SALES_MANAGER -> EnumSet.of(
                    TenantPermission.TEN_SALE_READ,
                    TenantPermission.TEN_SALE_WRITE,
                    TenantPermission.TEN_SALE_ISSUES_READ,
                    TenantPermission.TEN_REPORT_SALES_READ
            );

            case TENANT_BILLING_MANAGER -> EnumSet.of(
                    TenantPermission.TEN_BILLING_READ,
                    TenantPermission.TEN_BILLING_WRITE
            );

            case TENANT_READ_ONLY -> EnumSet.of(
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_INVENTORY_READ,
                    TenantPermission.TEN_USER_READ
            );

            // OPERATOR “operacional” (não administra usuários/config/billing)
            case TENANT_OPERATOR -> EnumSet.of(
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_INVENTORY_READ,
                    TenantPermission.TEN_INVENTORY_WRITE,
                    TenantPermission.TEN_SALE_READ
            );
        };
    }
}
