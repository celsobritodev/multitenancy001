package brito.com.multitenancy001.dtos;

import java.util.List;

import brito.com.multitenancy001.configuration.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
	    @NotBlank @Size(min = 2, max = 100)
	    String name,
	    
	    @Email @NotBlank
	    String email,
	    
	    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN,
             message = "Username inválido. Use 3-50 caracteres (letras, números, ., _, -)")
	    String username, // ✅ Opcional - se não informado, será gerado
	    
	    @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN,
             message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números")
	    String password,
	    
	    @NotBlank
	    String role,
	    
	    List<String> permissions
	) {}