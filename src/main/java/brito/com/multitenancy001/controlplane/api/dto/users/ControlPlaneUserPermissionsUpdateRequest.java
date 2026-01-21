package brito.com.multitenancy001.controlplane.api.dto.users;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ControlPlaneUserPermissionsUpdateRequest(
        @NotEmpty(message = "Lista de permissões não pode ser vazia")
        List<
                @Pattern(
                        regexp = "^CP_[A-Z0-9_]+$",
                        message = "Permissões de ControlPlane devem começar com CP_"
                )
                        String
                > permissions
) {}
