package brito.com.multitenancy001.tenant.categories.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request HTTP para criação de Subcategory no contexto do Tenant.
 */
public record SubcategoryCreateRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 100, message = "name deve ter no máximo 100 caracteres")
        String name
) {}