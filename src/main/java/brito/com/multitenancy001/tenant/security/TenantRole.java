package brito.com.multitenancy001.tenant.security;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum TenantRole implements RoleAuthority {

    TENANT_OWNER,
    TENANT_ADMIN,
    TENANT_SUPPORT,
    TENANT_USER,
    TENANT_PRODUCT_MANAGER,
    TENANT_SALES_MANAGER,
    TENANT_BILLING_MANAGER,
    TENANT_READ_ONLY,
    TENANT_OPERATOR;

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isTenantOwner() {
        return this == TENANT_OWNER;
    }
}

