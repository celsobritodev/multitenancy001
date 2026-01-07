package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.shared.security.RoleAuthority;

public enum ControlPlaneRole implements RoleAuthority {

    SUPER_ADMIN(true),
    SUPPORT(true),
	STAFF(true);

    private final boolean platformRole;

    ControlPlaneRole(boolean platformRole) {
        this.platformRole = platformRole;
    }

    @Override
    public String asAuthority() {
        return "ROLE_" + name();
    }

    // ✅ MÉTODO QUE FALTAVA
    public boolean isPlatformRole() {
        return platformRole;
    }

    // (Opcional, mas elegante)
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
