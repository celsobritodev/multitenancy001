package brito.com.multitenancy001.controlplane.billing.api.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.controlplane.billing.app.query.ControlPlanePaymentQueryService;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/billing/payments/query")
@RequiredArgsConstructor
public class ControlPlanePaymentQueryController {

    private final ControlPlanePaymentQueryService service;

    /**
     * Lista pagamentos por status (ex.: PENDING/COMPLETED/FAILED...).
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.name())")
    public ResponseEntity<List<PaymentResponse>> findByStatus(@PathVariable PaymentStatus status) {
        return ResponseEntity.ok(service.findByStatus(status));
    }

    /**
     * Soma total pago (COMPLETED) por conta em um período.
     */
    @GetMapping("/accounts/{accountId}/total-paid")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.name())"
            + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())"
    )
    public ResponseEntity<BigDecimal> getTotalPaidInPeriod(
            @PathVariable Long accountId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam("endDate")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(service.getTotalPaidInPeriod(accountId, startDate, endDate));
    }

    /**
     * Quantidade de pagamentos COMPLETED por conta.
     */
    @GetMapping("/accounts/{accountId}/count-completed")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.name())"
            + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())"
    )
    public ResponseEntity<Long> countCompletedPayments(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.countCompletedPayments(accountId));
    }
}
