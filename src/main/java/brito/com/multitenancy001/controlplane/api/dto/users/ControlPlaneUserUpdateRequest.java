package brito.com.multitenancy001.controlplane.api.dto.users;

import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ControlPlaneUserUpdateRequest(

        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String name,

        @Email(message = "Email inválido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        // opcional: só pode alterar se NÃO for system user (validado no service)
        @Pattern(
                regexp = ValidationPatterns.USERNAME_PATTERN,
                message = "Username inválido"
        )
        @Size(min = 3, max = 100, message = "Username deve ter entre 3 e 100 caracteres")
        String username,

        // opcional: troca de role (policy no service)
        ControlPlaneRole role,

        // opcional: só OWNER pode enviar e não pode ser system user (policy no service)
        List<
            @Pattern(
                regexp = "^CP_[A-Z0-9_]+$",
                message = "Permissões de ControlPlane devem começar com CP_"
            )
            String
        > permissions
) {}
