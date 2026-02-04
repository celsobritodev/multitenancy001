package brito.com.multitenancy001.controlplane.users.api.dto;

import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
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

        ControlPlaneRole role,

        List<
                @Pattern(
                        regexp = "^CP_[A-Z0-9_]+$",
                        message = "Permissões de ControlPlane devem começar com CP_"
                )
                String
        > permissions
) {}

