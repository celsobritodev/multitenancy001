package brito.com.multitenancy001.controlplane.api.dto.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

@Builder
public record ControlPlaneUserCreateRequest(
    
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    String name,
    
    @NotBlank(message = "Username é obrigatório")
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, message = "Username inválido...")
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
    
    @NotNull(message = "Role é obrigatória")
    ControlPlaneRole role,

    
    List<String> permissions,
    

    @Pattern(regexp = ValidationPatterns.PHONE_PATTERN, 
             message = "Telefone inválido")
    @Size(max = 20, message = "Telefone não pode exceder 20 caracteres")
    String phone,
    
    @Size(max = 500, message = "URL do avatar não pode exceder 500 caracteres")
    String avatarUrl
    
) {
    
    public ControlPlaneUserCreateRequest {
        if (phone != null) {
            phone = phone.trim();
        }
        if (avatarUrl != null) {
            avatarUrl = avatarUrl.trim();
        }
    }
}