package brito.com.multitenancy001.tenant.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TenantLoginInitRequest(
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "password é obrigatório")
        String password
) {}
