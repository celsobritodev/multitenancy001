package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum ControlPlaneRole implements RoleAuthority {

    PLATFORM_OWNER,
    PLATFORM_BILLING_MANAGER,  
    PLATFORM_SUPPORT,
    PLATFORM_OPERATOR;

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isSuperAdmin() {
        return this == PLATFORM_OWNER;
    }

    public boolean isBillingAdmin() {
        return this == PLATFORM_BILLING_MANAGER;
    }
}
