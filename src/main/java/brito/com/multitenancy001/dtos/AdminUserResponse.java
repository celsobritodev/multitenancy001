package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.entities.account.UserAccount;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        boolean active
) {

    public static AdminUserResponse from(UserAccount user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive()
        );
    }
}
