package brito.com.multitenancy001.tenant.debug.api;

import brito.com.multitenancy001.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
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
    public Map<String, Object> schema(
            @RequestHeader(name = "X-Tenant", required = false) String tenantHeaderRaw
    ) {
        String tenantHeader = (tenantHeaderRaw == null ? null : tenantHeaderRaw.trim());
        String tenantNormalized = StringUtils.hasText(tenantHeader) ? tenantHeader : null;

        // valida formato de schema (mesma ideia do provider/resolver)
        boolean valid = (tenantNormalized == null) || tenantNormalized.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");

        // se inválido, fica PUBLIC (null)
        String tenantToBind = valid ? tenantNormalized : null;

        try (TenantContext.Scope ignored = TenantContext.scope(tenantToBind)) {

            String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            String searchPath = jdbcTemplate.queryForObject("show search_path", String.class);

            return Map.of(
                    "tenant_header", tenantNormalized, // null se não veio / veio vazio
                    "current_schema", currentSchema,
                    "search_path", searchPath,
                    "header_valid", valid
            );
        }
    }
}

