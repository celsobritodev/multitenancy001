package brito.com.multitenancy001.controlplane.billing.api.admin;

import java.math.BigDecimal;
import java.time.Instant;
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
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.name())")
    public ResponseEntity<List<PaymentResponse>> findByStatus(@PathVariable PaymentStatus status) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.findByStatus(status));
    }

    // ControlPlanePaymentRepository.getTotalPaidInPeriod
    @GetMapping("/accounts/{accountId}/total-paid")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.name())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())"
    )
    public ResponseEntity<BigDecimal> totalPaid(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.getTotalPaidInPeriod(accountId, startDate, endDate));
    }

    // ControlPlanePaymentRepository.countCompletedPayments
    @GetMapping("/accounts/{accountId}/count-completed")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.name())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())"
    )
    public ResponseEntity<Long> countCompleted(@PathVariable Long accountId) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.countCompletedPayments(accountId));
    }
}

