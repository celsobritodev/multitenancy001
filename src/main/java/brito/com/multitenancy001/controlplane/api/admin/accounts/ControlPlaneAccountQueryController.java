package brito.com.multitenancy001.controlplane.api.admin.accounts;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.application.query.ControlPlaneAccountQueryService;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/accounts/query")
@RequiredArgsConstructor
public class ControlPlaneAccountQueryController {

    private final ControlPlaneAccountQueryService controlPlaneAccountQueryService;

    // AccountRepository.findEnabledById
    @GetMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getEnabledById(@PathVariable Long id) {
        return ResponseEntity.ok(controlPlaneAccountQueryService.getEnabledById(id));
    }

    // AccountRepository.findAnyById (inclui deleted)
    @GetMapping("/{id}/any")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getAnyById(@PathVariable Long id) {
        return ResponseEntity.ok(controlPlaneAccountQueryService.getAnyById(id));
    }

    // AccountRepository.countByStatusesAndDeletedFalse
    // Exemplo: /api/admin/accounts/query/count?statuses=ACTIVE&statuses=FREE_TRIAL
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Long> countByStatuses(@RequestParam List<AccountStatus> statuses) {
        return ResponseEntity.ok(controlPlaneAccountQueryService.countByStatusesNotDeleted(statuses));
    }

    // AccountRepository.findByPaymentDueDateBeforeAndDeletedFalse
    // Exemplo: /api/admin/accounts/query/payment-due/before?date=2026-01-01T00:00:00
    @GetMapping("/payment-due/before")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> findPaymentDueBefore(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date
    ) {
        return ResponseEntity.ok(controlPlaneAccountQueryService.findPaymentDueBeforeNotDeleted(date));
    }
}
