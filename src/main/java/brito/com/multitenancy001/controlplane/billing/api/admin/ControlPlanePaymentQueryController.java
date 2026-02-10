package brito.com.multitenancy001.controlplane.billing.api.admin;

import brito.com.multitenancy001.controlplane.billing.app.query.ControlPlanePaymentQueryService;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/billing/payments/query")
@RequiredArgsConstructor
public class ControlPlanePaymentQueryController {

    private final ControlPlanePaymentQueryService controlPlanePaymentQueryService;

    /**
     * Lista pagamentos por status (ex.: PENDING/COMPLETED/FAILED...).
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())")
    public ResponseEntity<List<PaymentResponse>> findByStatus(@PathVariable PaymentStatus status) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.findByStatus(status));
    }

    /**
     * Soma total pago (COMPLETED) por conta em um período.
     */
    @GetMapping("/accounts/{accountId}/total-paid")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
    )
    public ResponseEntity<BigDecimal> getTotalPaidInPeriod(
            @PathVariable Long accountId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam("endDate")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.getTotalPaidInPeriod(accountId, startDate, endDate));
    }

    /**
     * Quantidade de pagamentos COMPLETED por conta.
     */
    @GetMapping("/accounts/{accountId}/count-completed")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
    )
    public ResponseEntity<Long> countCompletedPayments(@PathVariable Long accountId) {
        return ResponseEntity.ok(controlPlanePaymentQueryService.countCompletedPayments(accountId));
    }
}
