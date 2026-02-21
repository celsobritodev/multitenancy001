package brito.com.multitenancy001.tenant.debug.api;

import brito.com.multitenancy001.tenant.debug.api.dto.TenantSchemaDebugResponse;
import brito.com.multitenancy001.tenant.debug.app.TenantDebugQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints DEV-only para depuração do multi-tenant.
 *
 * Regras:
 * - Somente no profile "dev".
 * - Controller NÃO injeta JdbcTemplate (ControllerComplianceVerifier).
 * - Delegar para Application Service.
 */
@RestController
@RequiredArgsConstructor
@Profile("dev")
public class TenantDebugController {

    private final TenantDebugQueryService tenantDebugQueryService;

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
            /* Header opcional de tenant para bind do schema. */
            @RequestHeader(name = "X-Tenant", required = false) String tenantHeaderRaw
    ) {
        TenantSchemaDebugResponse response = tenantDebugQueryService.getSchemaDebug(tenantHeaderRaw);
        return ResponseEntity.ok(response);
    }
}