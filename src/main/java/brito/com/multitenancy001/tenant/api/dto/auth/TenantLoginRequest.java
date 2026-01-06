package brito.com.multitenancy001.tenant.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginRequest(
	    @NotBlank String username,
	    @NotBlank String password,
	    @NotBlank String slug
	) {}
