package brito.com.multitenancy001.controlplane.api.dto.signup;

import brito.com.multitenancy001.controlplane.domain.account.TaxIdType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Nome da empresa é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        String displayName,

        @NotBlank(message = "Email da empresa é obrigatório")
        @Email(message = "Email inválido")
        String loginEmail,

        @NotNull(message = "Tipo de documento é obrigatório (CPF ou CNPJ)")
        TaxIdType taxIdType,

        @NotBlank(message = "Número do documento é obrigatório")
        String taxIdNumber,

        @NotBlank(message = "Senha é obrigatória")
        @Pattern(
        	    regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$",
        	    message = "Senha deve ter pelo menos 8 caracteres, contendo letras e números"
        	)
        String password,

        @NotBlank(message = "Confirmação de senha é obrigatória")
        String confirmPassword
) {
    @AssertTrue(message = "As senhas não coincidem")
    public boolean isPasswordMatching() {
        if (password == null || confirmPassword == null) return true;
        return password.equals(confirmPassword);
    }
}
