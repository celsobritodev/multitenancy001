package brito.com.multitenancy001.tenant.api.dto.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.LinkedHashSet;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantRole;

@Builder
public record TenantUserCreateRequest(
    
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    String name,
    
    @NotBlank(message = "Username é obrigatório")
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, message = "Username inválido...")
    @Size(min = 3, max = 50, message = "Username inválido. Use apenas letras, números, . e _")
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
    TenantRole role,
    
    @NotBlank
    @Pattern(
        regexp = "^TEN_[A-Z0-9_]+$",
        message = "Permission must follow TEN_* pattern (e.g. TEN_USER_CREATE)"
    )
    LinkedHashSet<String> permissions,
    
    // 🔹 CAMPOS NOVOS para UserTenant
    @Pattern(regexp = ValidationPatterns.PHONE_PATTERN, 
             message = "Telefone inválido")
    @Size(max = 20, message = "Telefone não pode exceder 20 caracteres")
    String phone,
    
    @Size(max = 500, message = "URL do avatar não pode exceder 500 caracteres")
    String avatarUrl
    
) {
    
	public TenantUserCreateRequest {
	    if (name != null) name = name.trim();
	    if (username != null) username = username.trim();
	    if (email != null) email = email.trim();

	    if (phone != null) phone = phone.trim();
	    if (avatarUrl != null) avatarUrl = avatarUrl.trim();
	}

}