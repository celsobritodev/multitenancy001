package brito.com.multitenancy001.tenant.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum TenantRole implements RoleAuthority {

    TENANT_ADMIN,
    ADMIN,
    PRODUCT_MANAGER,
    SALES_MANAGER,
    BILLING_ADMIN,
    VIEWER,
    USER;

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isTenantAdmin() {
        return this == TENANT_ADMIN;
    }
}
