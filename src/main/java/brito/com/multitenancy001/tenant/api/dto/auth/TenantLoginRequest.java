package brito.com.multitenancy001.tenant.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        Long accountId // opcional: só vem quando o front já escolheu o tenant
) {}
