package brito.com.multitenancy001.controlplane.billing.api.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.controlplane.billing.app.query.ControlPlanePaymentQueryService;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/billing/payments/query")
@RequiredArgsConstructor
public class ControlPlanePaymentQueryController {

    private final ControlPlanePaymentQueryService controlPlanePaymentQueryService;

    // ControlPlanePaymentRepository.findByStatus
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('CP_BILLING_READ')")
    public ResponseEntity<List<PaymentResponse>> findByStatus(@PathVariable PaymentStatus status) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.findByStatus(status));
    }

    // ControlPlanePaymentRepository.getTotalPaidInPeriod
    @GetMapping("/accounts/{accountId}/total-paid")
    @PreAuthorize("hasAuthority('CP_BILLING_READ') and hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<BigDecimal> totalPaid(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.getTotalPaidInPeriod(accountId, startDate, endDate));
    }

    // ControlPlanePaymentRepository.countCompletedPayments
    @GetMapping("/accounts/{accountId}/count-completed")
    @PreAuthorize("hasAuthority('CP_BILLING_READ') and hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Long> countCompleted(@PathVariable Long accountId) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.countCompletedPayments(accountId));
    }
}
