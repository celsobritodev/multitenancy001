package brito.com.multitenancy001.tenant.domain.security;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum TenantRole implements RoleAuthority {

    TENANT_ACCOUNT_OWNER,
    TENANT_ACCOUNT_ADMIN,
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
        return this == TENANT_ACCOUNT_OWNER;
    }
}
