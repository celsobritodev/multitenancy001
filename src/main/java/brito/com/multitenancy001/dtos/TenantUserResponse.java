package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.entities.tenant.TenantUser;

public record TenantUserResponse(
        Long id,
        String username,
        String email,
        String role,
        boolean active,
        boolean deleted
) {
    public static TenantUserResponse from(TenantUser user) {
        return new TenantUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                user.isDeleted()
        );
    }
}
