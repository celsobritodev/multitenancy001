package brito.com.multitenancy001.tenant.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
        @Pattern(
          regexp = ValidationPatterns.PASSWORD_PATTERN,
          message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas, números e caracteres especiais"
        )
        String newPassword
) {}
