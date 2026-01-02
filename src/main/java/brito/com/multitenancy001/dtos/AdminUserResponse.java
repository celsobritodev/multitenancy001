package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.platform.domain.user.PlatformUser;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        boolean active
) {

    public static AdminUserResponse from(PlatformUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive()
        );
    }
}
