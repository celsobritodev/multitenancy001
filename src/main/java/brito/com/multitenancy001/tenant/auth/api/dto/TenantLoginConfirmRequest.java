package brito.com.multitenancy001.tenant.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginConfirmRequest(
        @NotBlank String challengeId,
        Long accountId,
        String slug
) { }

