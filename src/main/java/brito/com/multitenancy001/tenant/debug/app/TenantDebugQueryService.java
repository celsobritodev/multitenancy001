package brito.com.multitenancy001.tenant.debug.app;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.debug.api.dto.TenantSchemaDebugResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Application Service (Tenant) para diagnóstico do contexto de schema/tenant.
 *
 * <p><b>Regras do projeto:</b></p>
 * <ul>
 *   <li>Controller não injeta {@code JdbcTemplate} diretamente (ControllerComplianceVerifier).</li>
 *   <li>Em TENANT, evitar {@code @Transactional} direto. Este serviço não declara transação.</li>
 * </ul>
 *
 * <p><b>Uso:</b> endpoint de debug para inspecionar {@code current_schema()} e {@code search_path},
 * e checar se o bind de {@link TenantContext} está sendo aplicado.</p>
 *
 * <p><b>Segurança:</b> não retorna dados sensíveis, apenas metadados do DB.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDebugQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Coleta informações úteis para diagnosticar:
     * <ul>
     *   <li>schema efetivo ({@code current_schema()})</li>
     *   <li>search_path ({@code show search_path})</li>
     *   <li>se o header/valor recebido é válido para bind de schema</li>
     * </ul>
     *
     * @param tenantHeaderRaw valor bruto recebido (ex.: header X-Tenant-Schema)
     * @return resposta com informações de debug do contexto
     */
    public TenantSchemaDebugResponse getSchemaDebug(String tenantHeaderRaw) {
        String tenantHeader = (tenantHeaderRaw == null ? null : tenantHeaderRaw.trim());
        String tenantNormalized = StringUtils.hasText(tenantHeader) ? tenantHeader : null;

        boolean valid = isValidSchemaIdentifierOrNull(tenantNormalized);

        // se inválido, não bindamos tenant (fica PUBLIC)
        String tenantToBind = valid ? tenantNormalized : null;

        if (log.isDebugEnabled()) {
            log.debug("🧪 TenantDebugQueryService.getSchemaDebug | raw='{}' | normalized='{}' | valid={} | bind='{}'",
                    tenantHeaderRaw, tenantNormalized, valid, tenantToBind);
        }

        try (TenantContext.Scope ignored = TenantContext.scope(tenantToBind)) {
            String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            String searchPath = jdbcTemplate.queryForObject("show search_path", String.class);

            if (log.isDebugEnabled()) {
                log.debug("🧪 TenantDebug result | bind='{}' | current_schema='{}' | search_path='{}'",
                        tenantToBind, currentSchema, searchPath);
            }

            return new TenantSchemaDebugResponse(
                    tenantNormalized,
                    currentSchema,
                    searchPath,
                    valid
            );
        }
    }

    /**
     * Valida se o schema pode ser bindado com segurança.
     *
     * <p>Regra: identificador SQL simples (sem aspas), estilo Postgres:
     * começa com letra/underscore, seguido de letras/números/underscore.</p>
     */
    private static boolean isValidSchemaIdentifierOrNull(String tenantSchema) {
        if (tenantSchema == null) return true; // null significa PUBLIC
        return tenantSchema.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}