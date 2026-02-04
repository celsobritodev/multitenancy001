package brito.com.multitenancy001.tenant.auth.api.dto;

public record TenantSelectionOption(
        Long accountId,
        String displayName,
        String slug
) { }

