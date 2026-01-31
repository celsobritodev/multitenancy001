package brito.com.multitenancy001.tenant.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TenantRefreshRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {}
