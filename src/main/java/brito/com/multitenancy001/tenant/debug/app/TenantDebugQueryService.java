package brito.com.multitenancy001.tenant.debug.app;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.debug.api.dto.TenantSchemaDebugResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Application Service (Tenant) para diagnóstico do contexto de schema/tenant.
 *
 * Regras:
 * - Controller não acessa JdbcTemplate diretamente.
 * - Acesso direto a SQL (JdbcTemplate) é permitido aqui como "query service" de infraestrutura leve.
 *
 * Observação:
 * - Este serviço é apenas diagnóstico; evite retornar dados sensíveis.
 */
@Service
@RequiredArgsConstructor
public class TenantDebugQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Coleta informações úteis para diagnosticar o schema atual / search_path,
     * forçando (opcionalmente) o bind do schema via TenantContext.
     */
    public TenantSchemaDebugResponse getSchemaDebug(String tenantHeaderRaw) {

        /* Normaliza e valida o header de tenant para bind seguro do schema. */
        String tenantHeader = (tenantHeaderRaw == null ? null : tenantHeaderRaw.trim());
        String tenantNormalized = StringUtils.hasText(tenantHeader) ? tenantHeader : null;

        // valida formato de schema (mesma ideia do provider/resolver)
        boolean valid = (tenantNormalized == null) || tenantNormalized.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");

        // se inválido, fica PUBLIC (null)
        String tenantToBind = valid ? tenantNormalized : null;

        try (TenantContext.Scope ignored = TenantContext.scope(tenantToBind)) {

            /* Consulta schema atual e search_path para inspeção. */
            String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            String searchPath = jdbcTemplate.queryForObject("show search_path", String.class);

            return new TenantSchemaDebugResponse(
                    tenantNormalized, // null se não veio / veio vazio
                    currentSchema,
                    searchPath,
                    valid
            );
        }
    }
}