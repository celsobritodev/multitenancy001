package brito.com.multitenancy001.tenant.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum TenantRole implements RoleAuthority {
    TENANT_ADMIN,
    MANAGER,
    VIEWER,
    USER;

    public boolean isAdmin() {
        return this == TENANT_ADMIN;
    }

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }
}
