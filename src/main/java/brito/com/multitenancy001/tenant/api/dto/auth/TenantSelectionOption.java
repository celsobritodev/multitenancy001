package brito.com.multitenancy001.tenant.api.dto.auth;

public record TenantSelectionOption(
        Long accountId,
        String displayName,
        String slug
) {}
