package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum ControlPlaneRole implements RoleAuthority {

    SUPER_ADMIN(true),
    BILLING_ADMIN(true),   // ✅ novo (plataforma)
    SUPPORT(true),
    STAFF(true);

    private final boolean controlPlaneRole;

    ControlPlaneRole(boolean newControPlaneRole) {
        this.controlPlaneRole = newControPlaneRole;
    }

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isControlPlaneRole() {
        return controlPlaneRole;
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isBillingAdmin() {
        return this == BILLING_ADMIN;
    }
}
