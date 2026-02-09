package brito.com.multitenancy001.controlplane.accounts.api.admin;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.query.ControlPlaneAccountQueryService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/controlplane/accounts/query")
@RequiredArgsConstructor
public class ControlPlaneAccountQueryController {

    private final ControlPlaneAccountQueryService controlPlaneAccountQueryService;
    private final AccountApiMapper accountApiMapper;

    @GetMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())")
    public ResponseEntity<AccountResponse> getEnabledById(@PathVariable Long id) {
        Account a = controlPlaneAccountQueryService.getEnabledById(id);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())")
    public ResponseEntity<AccountResponse> getAnyById(@PathVariable Long id) {
        Account a = controlPlaneAccountQueryService.getAnyById(id);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @PostMapping("/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())")
    public ResponseEntity<Long> countByStatuses(@RequestBody List<AccountStatus> statuses) {
        return ResponseEntity.ok(controlPlaneAccountQueryService.countByStatusesNotDeleted(statuses));
    }

    /**
     * Semântica: paymentDueDate é DATA CIVIL (LocalDate <-> DATE).
     * Endpoint aceita yyyy-MM-dd.
     *
     * Ex: /payment-due-before?date=2026-02-10
     */
    @GetMapping("/payment-due-before")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())")
    public ResponseEntity<List<AccountResponse>> findPaymentDueBefore(
            @RequestParam("date") String isoDate
    ) {
        LocalDate date = LocalDate.parse(isoDate);
        return ResponseEntity.ok(
                controlPlaneAccountQueryService.findPaymentDueBeforeNotDeleted(date)
                        .stream()
                        .map(accountApiMapper::toResponse)
                        .toList()
        );
    }
}
