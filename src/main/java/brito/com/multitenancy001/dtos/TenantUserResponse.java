package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.entities.tenant.UserTenant;

public record TenantUserResponse(
        Long id,
        String username,
        String email,
        String role,
        boolean active,
        boolean deleted
) {
    public static TenantUserResponse from(UserTenant user) {
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
