package brito.com.multitenancy001.tenant.sales.api;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import brito.com.multitenancy001.tenant.sales.app.command.TenantSaleCommandService;
import brito.com.multitenancy001.tenant.sales.app.query.TenantSaleQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant Sales endpoints.
 *
 * Rules:
 * - DTO-only controller (no entities in request/response).
 * - Tenant schema + accountId resolved from request identity.
 * - Write endpoints require TEN_SALE_WRITE; read endpoints require TEN_SALE_READ.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/sales")
public class TenantSaleController {

    private final TenantRequestIdentityService requestIdentity;
    private final TenantSaleCommandService commandService;
    private final TenantSaleQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleCreateRequest req) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();
        SaleResponse out = commandService.create(accountId, tenantSchema, req);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_READ.asAuthority())")
    public ResponseEntity<SaleResponse> getById(@PathVariable("id") UUID id) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();
        return ResponseEntity.ok(queryService.getById(accountId, tenantSchema, id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_READ.asAuthority())")
    public ResponseEntity<List<SaleResponse>> list(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "status", required = false) String status
    ) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();
        return ResponseEntity.ok(queryService.list(accountId, tenantSchema, from, to, status));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<SaleResponse> update(@PathVariable("id") UUID id,
                                              @Valid @RequestBody SaleUpdateRequest req) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();
        return ResponseEntity.ok(commandService.update(accountId, tenantSchema, id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();
        commandService.delete(accountId, tenantSchema, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<SaleResponse> restore(@PathVariable("id") UUID id) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();
        return ResponseEntity.ok(commandService.restore(accountId, tenantSchema, id));
    }
}