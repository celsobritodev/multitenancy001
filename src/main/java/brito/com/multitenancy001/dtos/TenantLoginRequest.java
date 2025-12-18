package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginRequest(
	    @NotBlank String username,
	    @NotBlank String password,
	    @NotBlank String slug
	) {}
