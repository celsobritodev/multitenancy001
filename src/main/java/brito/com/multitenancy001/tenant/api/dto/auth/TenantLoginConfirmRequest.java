package brito.com.multitenancy001.tenant.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TenantLoginConfirmRequest(
        @NotBlank String challengeId,
        @NotNull Long accountId
) {}
