package brito.com.multitenancy001.controlplane.security;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum ControlPlaneRole implements RoleAuthority {

    CONTROLPLANE_OWNER,
    CONTROLPLANE_BILLING_MANAGER,
    CONTROLPLANE_SUPPORT,
    CONTROLPLANE_OPERATOR;

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isOwner() {
        return this == CONTROLPLANE_OWNER;
    }

    public boolean isBillingManager() {
        return this == CONTROLPLANE_BILLING_MANAGER;
    }
}
