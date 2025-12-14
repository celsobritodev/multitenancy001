package brito.com.multitenancy001.dtos;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        boolean active
) {}
