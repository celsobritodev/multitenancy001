package brito.com.multitenancy001.tenant.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantRefreshRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {}

