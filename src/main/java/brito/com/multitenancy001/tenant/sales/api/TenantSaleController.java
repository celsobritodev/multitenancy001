package brito.com.multitenancy001.tenant.sales.api;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import brito.com.multitenancy001.tenant.sales.app.command.TenantSaleCommandService;
import brito.com.multitenancy001.tenant.sales.app.query.TenantSaleQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant Sales endpoints.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Controller trabalha apenas com DTOs.</li>
 *   <li>Tenant schema e accountId são resolvidos via {@link TenantRequestIdentityService}.</li>
 *   <li>Write endpoints exigem {@code TEN_SALE_WRITE}.</li>
 *   <li>Read endpoints exigem {@code TEN_SALE_READ}.</li>
 * </ul>
 *
 * <p>Integração com customers:</p>
 * <ul>
 *   <li>Create/Update aceitam {@code customerId} no body.</li>
 *   <li>List permite filtro opcional por {@code customerId}.</li>
 * </ul>
 *
 * <p>Paginação:</p>
 * <ul>
 *   <li>O endpoint de listagem usa {@link Pageable} no mesmo padrão do restante do projeto.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/sales")
@Slf4j
public class TenantSaleController {

    private final TenantRequestIdentityService requestIdentity;
    private final TenantSaleCommandService commandService;
    private final TenantSaleQueryService queryService;

    /**
     * Cria uma nova venda.
     *
     * @param req payload de criação
     * @return venda criada
     */
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleCreateRequest req) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();

        log.info("➡️ POST /api/tenant/sales | accountId={} tenantSchema={} customerId={}",
                accountId, tenantSchema, req.customerId());

        SaleResponse out = commandService.create(accountId, tenantSchema, req);
        return ResponseEntity.ok(out);
    }

    /**
     * Busca uma venda por ID.
     *
     * @param id identificador da venda
     * @return venda encontrada
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_READ.asAuthority())")
    public ResponseEntity<SaleResponse> getById(@PathVariable("id") UUID id) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();

        log.info("➡️ GET /api/tenant/sales/{} | accountId={} tenantSchema={}",
                id, accountId, tenantSchema);

        return ResponseEntity.ok(queryService.getById(accountId, tenantSchema, id));
    }

    /**
     * Lista vendas com filtros opcionais e paginação.
     *
     * @param from data inicial opcional
     * @param to data final opcional
     * @param status status opcional
     * @param customerId customer opcional
     * @param pageable paginação/sort
     * @return página de vendas
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_READ.asAuthority())")
    public ResponseEntity<Page<SaleResponse>> list(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customerId", required = false) UUID customerId,
            Pageable pageable
    ) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();

        log.info("➡️ GET /api/tenant/sales | accountId={} tenantSchema={} from={} to={} status={} customerId={} pageable={}",
                accountId, tenantSchema, from, to, status, customerId, pageable);

        return ResponseEntity.ok(
                queryService.list(accountId, tenantSchema, from, to, status, customerId, pageable)
        );
    }

    /**
     * Atualiza uma venda existente.
     *
     * @param id identificador da venda
     * @param req payload de atualização
     * @return venda atualizada
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<SaleResponse> update(@PathVariable("id") UUID id,
                                               @Valid @RequestBody SaleUpdateRequest req) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();

        log.info("➡️ PUT /api/tenant/sales/{} | accountId={} tenantSchema={} customerId={}",
                id, accountId, tenantSchema, req.customerId());

        return ResponseEntity.ok(commandService.update(accountId, tenantSchema, id, req));
    }

    /**
     * Deleta logicamente uma venda.
     *
     * @param id identificador da venda
     * @return no-content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();

        log.info("➡️ DELETE /api/tenant/sales/{} | accountId={} tenantSchema={}",
                id, accountId, tenantSchema);

        commandService.delete(accountId, tenantSchema, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restaura uma venda deletada logicamente.
     *
     * @param id identificador da venda
     * @return venda restaurada
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SALE_WRITE.asAuthority())")
    public ResponseEntity<SaleResponse> restore(@PathVariable("id") UUID id) {
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long accountId = requestIdentity.getCurrentAccountId();

        log.info("➡️ POST /api/tenant/sales/{}/restore | accountId={} tenantSchema={}",
                id, accountId, tenantSchema);

        return ResponseEntity.ok(commandService.restore(accountId, tenantSchema, id));
    }
}