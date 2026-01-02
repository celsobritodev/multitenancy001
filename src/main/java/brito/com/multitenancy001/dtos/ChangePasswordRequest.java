package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
	    @NotBlank 
	    String currentPassword,
	    
	    @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN,
	             message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números")
	    @NotBlank 
	    String newPassword,
	    
	    @NotBlank 
	    String confirmPassword
	) {}