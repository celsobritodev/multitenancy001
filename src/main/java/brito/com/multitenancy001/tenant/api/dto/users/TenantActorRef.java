package brito.com.multitenancy001.tenant.api.dto.users;

public record TenantActorRef(
        Long userId,
        String email
) {}
