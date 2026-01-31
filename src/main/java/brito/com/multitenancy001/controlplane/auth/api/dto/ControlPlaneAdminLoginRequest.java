package brito.com.multitenancy001.controlplane.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ControlPlaneAdminLoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
