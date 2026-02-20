package brito.com.multitenancy001.tenant.categories.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request HTTP para criação de Category no contexto do Tenant.
 *
 * Regras:
 * - Controller recebe DTO (nunca Entity)
 * - Validações básicas ficam no DTO; regras de negócio ficam no Service/Domain
 */
public record CategoryCreateRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 100, message = "name deve ter no máximo 100 caracteres")
        String name
) {}