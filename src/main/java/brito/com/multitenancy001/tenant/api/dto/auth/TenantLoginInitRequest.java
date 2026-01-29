package brito.com.multitenancy001.tenant.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginInitRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
