package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum ControlPlaneRole implements RoleAuthority {

    SUPER_ADMIN,
    BILLING_ADMIN_CP,   // ou BILLING_ADMIN_CP se quiser manter sufixo
    SUPPORT,
    STAFF;

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isBillingAdmin() {
        return this == BILLING_ADMIN_CP;
    }
}
