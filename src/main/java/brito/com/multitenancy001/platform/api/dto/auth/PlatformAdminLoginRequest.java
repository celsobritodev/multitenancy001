package brito.com.multitenancy001.platform.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record PlatformAdminLoginRequest(
	    @NotBlank String username,
	    @NotBlank String password
	) {}