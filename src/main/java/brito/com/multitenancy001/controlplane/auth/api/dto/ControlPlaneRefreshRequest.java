package brito.com.multitenancy001.controlplane.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request de refresh do Control Plane.
 */
public record ControlPlaneRefreshRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {}
