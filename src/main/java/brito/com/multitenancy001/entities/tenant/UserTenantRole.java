package brito.com.multitenancy001.entities.tenant;

import brito.com.multitenancy001.dtos.RoleAuthority;

public enum UserTenantRole implements RoleAuthority {
    TENANT_ADMIN,
    MANAGER,
    USER;

    public boolean isAdmin() {
        return this == TENANT_ADMIN;
    }

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }
}

