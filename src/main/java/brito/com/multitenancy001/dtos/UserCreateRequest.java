package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import brito.com.multitenancy001.configuration.ValidationPatterns;

import java.util.List;

@Builder
public record UserCreateRequest(
    
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    String name,
    
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, 
             message = "Username inválido. Use apenas letras, números, . e _")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    String username,
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "Email não pode exceder 150 caracteres")
    String email,
    
    @NotBlank(message = "Senha é obrigatória")
    @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN, 
             message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas, números e caracteres especiais")
    String password,
    
    @NotBlank(message = "Role é obrigatória")
    @Pattern(regexp = "ADMIN|PRODUCT_MANAGER|SALES_MANAGER|VIEWER|SUPPORT|FINANCEIRO|OPERACOES", 
             message = "Role inválida")
    String role,
    
    List<String> permissions,
    
    // 🔹 CAMPOS NOVOS para UserTenant
    @Pattern(regexp = ValidationPatterns.PHONE_PATTERN, 
             message = "Telefone inválido")
    @Size(max = 20, message = "Telefone não pode exceder 20 caracteres")
    String phone,
    
    @Size(max = 500, message = "URL do avatar não pode exceder 500 caracteres")
    String avatarUrl
    
) {
    
    public UserCreateRequest {
        if (phone != null) {
            phone = phone.trim();
        }
        if (avatarUrl != null) {
            avatarUrl = avatarUrl.trim();
        }
    }
}