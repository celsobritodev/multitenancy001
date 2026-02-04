package brito.com.multitenancy001.tenant.auth.api.dto;

public record AccountSelectionOption(
        Long accountId,
        String displayName,
        String slug
) {}

