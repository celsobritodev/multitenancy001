package brito.com.multitenancy001.controlplane.users.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ControlPlaneChangeMyPasswordRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        @Size(min = 8, max = 72, message = "Senha atual deve ter entre 8 e 72 caracteres")
        String currentPassword,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres")
        String newPassword,

        @NotBlank(message = "Confirmar senha é obrigatório")
        @Size(min = 8, max = 72, message = "Confirmar senha deve ter entre 8 e 72 caracteres")
        String confirmPassword
) {}

