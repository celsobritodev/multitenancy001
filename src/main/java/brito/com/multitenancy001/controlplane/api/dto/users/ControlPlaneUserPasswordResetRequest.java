package brito.com.multitenancy001.controlplane.api.dto.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ControlPlaneUserPasswordResetRequest(

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        String newPassword,

        @NotBlank(message = "Confirmar senha é obrigatório")
        @Size(min = 8, max = 72, message = "Confirmar senha deve ter entre 8 e 72 caracteres")
        String confirmPassword

) {}
