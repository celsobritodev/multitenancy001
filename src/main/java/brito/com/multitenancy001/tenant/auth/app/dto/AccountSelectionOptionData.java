package brito.com.multitenancy001.tenant.auth.app.dto;

public record AccountSelectionOptionData(
        Long accountId,
        String displayName,
        String slug
) { }
