package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum ControlPlaneRole implements RoleAuthority {

    SUPER_ADMIN(true),
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

    // ✅ MÉTODO QUE FALTAVA
    public boolean isControlPlaneRole() {
        return controlPlaneRole;
    }

    // (Opcional, mas elegante)
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
