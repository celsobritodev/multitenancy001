package brito.com.multitenancy001.tenant.users.api.dto;

public record TenantActorRef(
        Long userId,
        String email
) {}

