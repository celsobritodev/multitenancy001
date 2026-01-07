// src/main/java/brito/com/multitenancy001/dtos/SignupRequest.java
package brito.com.multitenancy001.controlplane.api.dto.signup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import brito.com.multitenancy001.controlplane.domain.account.DocumentType;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

public record SignupRequest(

    @NotBlank(message = "Nome da empresa é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    String name,

    @NotBlank(message = "Email da empresa é obrigatório")
    @Email(message = "Email inválido")
    String companyEmail,

    @NotNull(message = "Tipo de documento é obrigatório (CPF ou CNPJ)")
    DocumentType companyDocType,

    @NotBlank(message = "Número do documento é obrigatório")
    String companyDocNumber,

    @NotBlank(message = "Senha é obrigatória")
    @Pattern(
        regexp = ValidationPatterns.PASSWORD_PATTERN,
        message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números"
    )
    String password,

    @NotBlank(message = "Confirmação de senha é obrigatória")
    String confirmPassword
) {
    public SignupRequest {
        if (password != null && confirmPassword != null && !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("As senhas não coincidem");
        }
    }
}
