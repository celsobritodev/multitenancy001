package brito.com.multitenancy001.tenant.me.api.dto;

import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantChangeMyPasswordRequest(

        @NotBlank(message = "Senha atual é obrigatória")
        @Size(min = 8, max = 72, message = "Senha atual deve ter entre 8 e 72 caracteres")
        String currentPassword,

        @NotBlank(message = "Nova senha é obrigatória")
        @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN, message = "Senha fraca / inválida")
        @Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres")
        String newPassword,

        @NotBlank(message = "Confirmar nova senha é obrigatório")
        @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN, message = "Senha fraca / inválida")
        @Size(min = 8, max = 72, message = "Confirmar nova senha deve ter entre 8 e 72 caracteres")
        String confirmNewPassword
) {}
