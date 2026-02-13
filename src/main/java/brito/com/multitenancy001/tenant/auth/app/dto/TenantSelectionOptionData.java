package brito.com.multitenancy001.tenant.auth.app.dto;

public record TenantSelectionOptionData(
        Long accountId,
        String displayName,
        String slug
) { }

