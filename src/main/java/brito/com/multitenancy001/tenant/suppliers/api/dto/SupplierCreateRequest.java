package brito.com.multitenancy001.tenant.suppliers.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request HTTP para criação de Supplier no contexto do Tenant.
 *
 * Regras:
 * - Controller recebe DTO (nunca Entity)
 * - Validações básicas ficam no DTO; regras de negócio ficam no Service/Domain
 */
public record SupplierCreateRequest(

        @NotBlank(message = "name é obrigatório")
        @Size(max = 200, message = "name deve ter no máximo 200 caracteres")
        String name,

        @Size(max = 100, message = "contactPerson deve ter no máximo 100 caracteres")
        String contactPerson,

        @Email(message = "email inválido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres")
        String email,

        @Size(max = 20, message = "phone deve ter no máximo 20 caracteres")
        String phone,

        String address,

        @Size(max = 20, message = "document deve ter no máximo 20 caracteres")
        String document,

        @Size(max = 10, message = "documentType deve ter no máximo 10 caracteres")
        String documentType,

        @Size(max = 200, message = "website deve ter no máximo 200 caracteres")
        String website,

        @Size(max = 100, message = "paymentTerms deve ter no máximo 100 caracteres")
        String paymentTerms,

        Integer leadTimeDays,

        BigDecimal rating,

        String notes
) {}