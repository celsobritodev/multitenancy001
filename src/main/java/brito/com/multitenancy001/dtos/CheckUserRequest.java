// src/main/java/brito/com/example/multitenancy001/dtos/CheckUserRequest.java
package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.NotBlank;

public record CheckUserRequest(
		
	@NotBlank(message = "Slug da conta é obrigatório")
    String slug,	
		
    @NotBlank(message = "Username é obrigatório")
    String username,
    
    @NotBlank(message = "Password é obrigatório")
    String password
) {}