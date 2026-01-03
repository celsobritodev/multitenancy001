// src/main/java/brito/com/multitenancy001/dtos/TenantUserCreateRequest.java
package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

public record TenantUserCreateRequest(
    
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    String name,           // User's full name
    
    @NotBlank(message = "Username é obrigatório")
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, 
             message = "Username inválido. Use apenas letras, números, . e _")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    String username,       // Login username (can repeat across tenants)
    
    @NotBlank(message = "Senha é obrigatória")
    @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN, 
             message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números")
    String password,       // User password
    
    @NotBlank(message = "Role é obrigatória")
    String role            // TENANT_ADMIN, MANAGER, VIEWER, USER
    
) {}