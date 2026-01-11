package brito.com.multitenancy001.tenant.domain.security;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum TenantRole implements RoleAuthority {

    TENANT_OWNER,
    TENANT_ADMIN,
    PRODUCT_MANAGER,
    SALES_MANAGER,
    BILLING_ADMIN_TN,
    VIEWER,
    USER;

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isTenantOwner() {
        return this == TENANT_OWNER;
    }
}
