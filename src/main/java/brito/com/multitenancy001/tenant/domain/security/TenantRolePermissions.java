package brito.com.multitenancy001.tenant.domain.security;

import java.util.EnumSet;
import java.util.Set;

public final class TenantRolePermissions {

    private TenantRolePermissions() {}

    public static Set<TenantPermission> permissionsFor(TenantRole role) {
        return switch (role) {
            case TENANT_OWNER -> EnumSet.allOf(TenantPermission.class);

            case TENANT_ADMIN -> EnumSet.of(
                    TenantPermission.TEN_USER_READ,
                    TenantPermission.TEN_USER_CREATE,
                    TenantPermission.TEN_USER_UPDATE,
                    TenantPermission.TEN_USER_SUSPEND,
                    TenantPermission.TEN_USER_RESTORE,
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_PRODUCT_WRITE,
                    TenantPermission.TEN_INVENTORY_READ,
                    TenantPermission.TEN_BILLING_READ,
                    TenantPermission.TEN_SETTINGS_READ,
                    TenantPermission.TEN_SETTINGS_WRITE
            );

            case PRODUCT_MANAGER -> EnumSet.of(
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_PRODUCT_WRITE,
                    TenantPermission.TEN_INVENTORY_READ
            );

            case SALES_MANAGER -> EnumSet.of(
                    TenantPermission.TEN_SALE_READ,
                    TenantPermission.TEN_SALE_WRITE
            );

            case BILLING_ADMIN_TN -> EnumSet.of(
                    TenantPermission.TEN_BILLING_READ,
                    TenantPermission.TEN_BILLING_WRITE
            );

            case VIEWER -> EnumSet.of(
                    TenantPermission.TEN_PRODUCT_READ,
                    TenantPermission.TEN_INVENTORY_READ,
                    TenantPermission.TEN_USER_READ
            );

            case USER -> EnumSet.of(
                    TenantPermission.TEN_PRODUCT_READ
            );
        };
    }
}
