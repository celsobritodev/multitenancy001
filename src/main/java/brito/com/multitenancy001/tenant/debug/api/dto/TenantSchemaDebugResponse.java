package brito.com.multitenancy001.tenant.debug.api.dto;

/**
 * DTO de resposta para diagnóstico do schema do Tenant.
 *
 * Campos:
 * - tenantHeader: valor do header recebido (se houver)
 * - currentSchema: resultado de current_schema()
 * - searchPath: resultado do SHOW search_path
 * - valid: sanity-check simples do contexto
 */
public record TenantSchemaDebugResponse(
        String tenantHeader,
        String currentSchema,
        String searchPath,
        boolean valid
) {}