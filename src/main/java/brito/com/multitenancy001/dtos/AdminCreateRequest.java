package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.configuration.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateRequest(

	    @NotBlank
	    @Size(min = 3, max = 50)
	    String username,

	    @NotBlank
	    @Email
	    String email,

	    @NotBlank
	    @Pattern(
	        regexp = ValidationPatterns.PASSWORD_PATTERN,
	        message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números."
	    )
	    String password,

	    @NotBlank
	    String confirmPassword
	) {}
