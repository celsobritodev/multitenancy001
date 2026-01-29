package brito.com.multitenancy001.tenant.domain.user.permission;

import brito.com.multitenancy001.tenant.security.TenantPermission;

public record TenantUserPermission(String code) {

    public TenantUserPermission {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Permission code cannot be null/blank");
        }
    }

    /**
     * Factory segura para criar a partir do enum TenantPermission.
     * Garante que o código persistido seja sempre o name() do enum.
     */
    public static TenantUserPermission from(TenantPermission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        return new TenantUserPermission(permission.name());
    }
}
