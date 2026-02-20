package brito.com.multitenancy001.tenant.categories.api.dto;

/**
 * Response HTTP de Category (contrato da API do Tenant).
 */
public record CategoryResponse(
        Long id,
        String name,
        boolean active,
        boolean deleted
) {}