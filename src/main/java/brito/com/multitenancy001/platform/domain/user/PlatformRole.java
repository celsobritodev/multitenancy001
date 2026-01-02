package brito.com.multitenancy001.platform.domain.user;

import brito.com.multitenancy001.security.RoleAuthority;

public enum PlatformRole implements RoleAuthority {

    SUPER_ADMIN(true),
    SUPPORT(true),
	STAFF(true);

    private final boolean platformRole;

    PlatformRole(boolean platformRole) {
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
