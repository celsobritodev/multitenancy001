package brito.com.multitenancy001.tenant.security;

import brito.com.multitenancy001.shared.security.PermissionAuthority;

public enum TenantPermission implements PermissionAuthority {

    TEN_USER_READ,
    TEN_USER_CREATE,
    TEN_USER_UPDATE,
    TEN_USER_SUSPEND,
    TEN_USER_RESTORE,
    TEN_USER_DELETE,

    TEN_ROLE_TRANSFER, 

    TEN_PRODUCT_READ,
    TEN_PRODUCT_WRITE,

    TEN_CATEGORY_READ,
    TEN_CATEGORY_WRITE,

    TEN_SUPPLIER_READ,
    TEN_SUPPLIER_WRITE,

    TEN_SALE_READ,
    TEN_SALE_WRITE,
    TEN_SALE_ISSUES_READ,

    TEN_REPORT_SALES_READ,

    TEN_BILLING_READ,
    TEN_BILLING_WRITE,

    TEN_SETTINGS_READ,
    TEN_SETTINGS_WRITE,
	
	TEN_INVENTORY_READ,
	TEN_INVENTORY_WRITE;

    @Override
    public String asAuthority() {
        return name();
    }
}
