package brito.com.multitenancy001.tenant.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginInitRequest(
        @NotBlank String email,
        @NotBlank String password
) {}

