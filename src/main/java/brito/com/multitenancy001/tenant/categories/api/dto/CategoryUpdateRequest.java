package brito.com.multitenancy001.tenant.categories.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request HTTP para atualização de Category no contexto do Tenant.
 *
 * Observação:
 * - Aqui escolhi exigir "name" (PUT semântico = substituição completa).
 * - Se você preferir PATCH parcial, trocamos NotBlank por @Size e aceitamos null.
 */
public record CategoryUpdateRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 100, message = "name deve ter no máximo 100 caracteres")
        String name
) {}