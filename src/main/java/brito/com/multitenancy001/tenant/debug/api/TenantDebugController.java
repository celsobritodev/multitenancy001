package brito.com.multitenancy001.tenant.debug.api;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.debug.api.dto.TenantSchemaDebugResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints DEV-only para depuração do multi-tenant.
 *
 * Regras:
 * - Somente no profile "dev".
 * - Não depende de JWT para bind de schema (permite validar resolução de schema isoladamente).
 * - Retorna payload tipado (DTO) para contrato consistente.
 */
@RestController
@RequiredArgsConstructor
@Profile("dev")
public class TenantDebugController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * DEBUG/DEV:
     * Força o tenant via header X-Tenant para validar o multi-tenant SEM depender de JWT.
     *
     * Exemplo:
     * GET /api/tenant/_debug/schema
     * Header: X-Tenant: t_foton_devices_6d79df
     */
    @GetMapping("/api/tenant/_debug/schema")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SETTINGS_READ.asAuthority())")
    public ResponseEntity<TenantSchemaDebugResponse> schema(
            @RequestHeader(name = "X-Tenant", required = false) String tenantHeaderRaw
    ) {
        /* Normaliza e valida o header de tenant para bind seguro do schema. */
        String tenantHeader = (tenantHeaderRaw == null ? null : tenantHeaderRaw.trim());
        String tenantNormalized = StringUtils.hasText(tenantHeader) ? tenantHeader : null;

        // valida formato de schema (mesma ideia do provider/resolver)
        boolean valid = (tenantNormalized == null) || tenantNormalized.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");

        // se inválido, fica PUBLIC (null)
        String tenantToBind = valid ? tenantNormalized : null;

        try (TenantContext.Scope ignored = TenantContext.scope(tenantToBind)) {

            String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            String searchPath = jdbcTemplate.queryForObject("show search_path", String.class);

            TenantSchemaDebugResponse response = new TenantSchemaDebugResponse(
                    tenantNormalized, // null se não veio / veio vazio
                    currentSchema,
                    searchPath,
                    valid
            );

            return ResponseEntity.ok(response);
        }
    }
}