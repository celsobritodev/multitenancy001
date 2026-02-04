package brito.com.multitenancy001.controlplane.accounts.api.admin;

import java.time.LocalDate;
import java.util.List;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.query.ControlPlaneAccountQueryService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/controlplane/accounts/query")
@RequiredArgsConstructor
public class ControlPlaneAccountQueryController {

    private final ControlPlaneAccountQueryService queryService;
    private final AccountApiMapper accountApiMapper;

    @GetMapping("/{id}/enabled")
    public ResponseEntity<AccountResponse> getEnabledById(@PathVariable Long id) {
        Account a = queryService.getEnabledById(id);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAnyById(@PathVariable Long id) {
        Account a = queryService.getAnyById(id);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @PostMapping("/count")
    public ResponseEntity<Long> countByStatuses(@RequestBody List<AccountStatus> statuses) {
        return ResponseEntity.ok(queryService.countByStatusesNotDeleted(statuses));
    }

    /**
     * Semântica: paymentDueDate é DATA CIVIL (LocalDate <-> DATE).
     * Endpoint aceita yyyy-MM-dd.
     *
     * Ex: /payment-due-before?date=2026-02-10
     */
    @GetMapping("/payment-due-before")
    public ResponseEntity<List<AccountResponse>> findPaymentDueBefore(
            @RequestParam("date") String isoDate
    ) {
        LocalDate date = LocalDate.parse(isoDate);
        return ResponseEntity.ok(
                queryService.findPaymentDueBeforeNotDeleted(date)
                        .stream()
                        .map(accountApiMapper::toResponse)
                        .toList()
        );
    }
}
