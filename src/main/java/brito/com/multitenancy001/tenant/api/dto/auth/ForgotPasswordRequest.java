package brito.com.multitenancy001.tenant.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank String slug,
        @NotBlank @Email String email
) {}
