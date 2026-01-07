package brito.com.multitenancy001.controlplane.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ControlPlaneAdminLoginRequest(
	    @NotBlank String username,
	    @NotBlank String password
	) {}