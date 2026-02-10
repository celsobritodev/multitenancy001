// src/main/java/brito/com/multitenancy001/tenant/users/domain/permission/TenantUserPermission.java
package brito.com.multitenancy001.tenant.users.domain.permission;

import brito.com.multitenancy001.tenant.security.TenantPermission;

import java.util.Locale;

public record TenantUserPermission(String code) {

    public TenantUserPermission {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Permission code cannot be null/blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("TEN_")) {
            throw new IllegalArgumentException("Tenant permission must start with TEN_: " + normalized);
        }
        code = normalized;
    }

    public static TenantUserPermission from(TenantPermission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        return new TenantUserPermission(permission.asAuthority()); // name() -> asAuthority()
    }
}
