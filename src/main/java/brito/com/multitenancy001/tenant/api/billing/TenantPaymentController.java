package brito.com.multitenancy001.tenant.api.billing;


import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.tenant.application.billing.TenantBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/billing/payments")
@RequiredArgsConstructor
public class TenantPaymentController {

    private final TenantBillingService tenantBillingService;

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAuthority('TEN_BILLING_READ')")
    public ResponseEntity<List<PaymentResponse>> listPayments(@PathVariable Long accountId) {
        return ResponseEntity.ok(tenantBillingService.listPaymentsForAccount(accountId));
    }

    @GetMapping("/account/{accountId}/{paymentId}")
    @PreAuthorize("hasAuthority('TEN_BILLING_READ')")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long accountId, @PathVariable Long paymentId) {
        return ResponseEntity.ok(tenantBillingService.getPaymentForAccount(accountId, paymentId));
    }

    @GetMapping("/account/{accountId}/has-active")
    @PreAuthorize("hasAuthority('TEN_BILLING_READ')")
    public ResponseEntity<Boolean> hasActive(@PathVariable Long accountId) {
        return ResponseEntity.ok(tenantBillingService.hasActivePayment(accountId));
    }
}
