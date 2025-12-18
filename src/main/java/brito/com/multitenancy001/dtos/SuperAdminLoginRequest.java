package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.NotBlank;

public record SuperAdminLoginRequest(
	    @NotBlank String username,
	    @NotBlank String password
	) {}