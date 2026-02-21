package brito.com.multitenancy001.tenant.debug.api.dto;

/**
 * Payload tipado para endpoints de debug do tenant.
 *
 * Motivação:
 * - Evitar Map<String,Object> no controller (contrato explícito e versionável).
 * - Facilitar testes E2E e evoluções sem quebrar consumidores por "chave solta".
 *
 * Observação:
 * - Este DTO é DEV-only (usado em endpoints sob profile "dev").
 */
public record TenantSchemaDebugResponse(
        String tenantHeader,
        String currentSchema,
        String searchPath,
        boolean headerValid
) {
}