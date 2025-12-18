package brito.com.multitenancy001.entities.account;



import brito.com.multitenancy001.dtos.RoleAuthority;

public enum UserAccountRole implements RoleAuthority {

    SUPER_ADMIN(true),
    PLATFORM_ADMIN(true),
    SUPPORT(false);

    private final boolean platformRole;

    UserAccountRole(boolean platformRole) {
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
