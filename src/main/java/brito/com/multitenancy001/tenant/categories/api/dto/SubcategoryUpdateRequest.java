package brito.com.multitenancy001.tenant.categories.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request HTTP para atualização de Subcategory (PUT semântico).
 */
public record SubcategoryUpdateRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 100, message = "name deve ter no máximo 100 caracteres")
        String name
) {}