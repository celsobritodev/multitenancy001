package brito.com.multitenancy001.controlplane.users.api.dto;

import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
public record ControlPlaneUserCreateRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        @Size(max = 150, message = "Email não pode exceder 150 caracteres")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Pattern(
                regexp = ValidationPatterns.PASSWORD_PATTERN,
                message = "Senha fraca / inválida"
        )
        String password,

        @NotNull(message = "Role é obrigatória")
        ControlPlaneRole role,

        List<String> permissions

 
   
) {
    public ControlPlaneUserCreateRequest {
    
        if (email != null) email = email.trim().toLowerCase();
    }
}

