package brito.com.multitenancy001.controlplane.accounts.api.admin;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountCountByStatusesRequest;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.query.ControlPlaneAccountQueryService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de consulta (query) para Accounts no Control Plane.
 *
 * Regras:
 * - Controller não acessa repositórios diretamente (somente services).
 * - Contratos de request/response preferem DTOs (evita payload "solto").
 */
@RestController
@RequestMapping("/api/controlplane/accounts/query")
@RequiredArgsConstructor
public class ControlPlaneAccountQueryController {

    private final ControlPlaneAccountQueryService controlPlaneAccountQueryService;
    private final AccountApiMapper accountApiMapper;

    @GetMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<AccountResponse> getEnabledById(@PathVariable Long id) {
        /* Busca conta habilitada por id (regra de negócio no service). */
        Account a = controlPlaneAccountQueryService.getEnabledById(id);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<AccountResponse> getAnyById(@PathVariable Long id) {
        /* Busca conta por id sem exigir estado enabled (regra de negócio no service). */
        Account a = controlPlaneAccountQueryService.getAnyById(id);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @PostMapping("/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<Long> countByStatuses(@Valid @RequestBody AccountCountByStatusesRequest request) {
        /* Conta accounts por statuses (excluindo deletadas), com contrato tipado. */
        return ResponseEntity.ok(
                controlPlaneAccountQueryService.countByStatusesNotDeleted(request.statuses())
        );
    }

    /**
     * Semântica: paymentDueDate é DATA CIVIL (LocalDate <-> DATE).
     * Endpoint aceita yyyy-MM-dd.
     *
     * Ex: /payment-due-before?date=2026-02-10
     */
    @GetMapping("/payment-due-before")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<List<AccountResponse>> findPaymentDueBefore(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        /* Lista accounts com paymentDueDate anterior (excluindo deletadas). */
        return ResponseEntity.ok(
                controlPlaneAccountQueryService.findPaymentDueBeforeNotDeleted(date)
                        .stream()
                        .map(accountApiMapper::toResponse)
                        .toList()
        );
    }
}