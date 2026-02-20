package brito.com.multitenancy001.tenant.categories.api.dto;

/**
 * Response HTTP de Subcategory (contrato da API do Tenant).
 */
public record SubcategoryResponse(
        Long id,
        Long categoryId,
        String name,
        boolean active,
        boolean deleted
) {}