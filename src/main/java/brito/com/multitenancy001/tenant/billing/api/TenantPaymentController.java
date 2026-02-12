package brito.com.multitenancy001.tenant.billing.api;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.tenant.billing.app.TenantPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/billing/payments")
@RequiredArgsConstructor
public class TenantPaymentController {

    private final TenantPaymentService tenantBillingService;

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_BILLING_READ.name())")
    public ResponseEntity<List<PaymentResponse>> listPayments(@PathVariable Long accountId) {
        return ResponseEntity.ok(tenantBillingService.listPaymentsForAccount(accountId));
    }

    @GetMapping("/account/{accountId}/{paymentId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_BILLING_READ.name())")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long accountId, @PathVariable Long paymentId) {
        return ResponseEntity.ok(tenantBillingService.getPaymentForAccount(accountId, paymentId));
    }

    @GetMapping("/account/{accountId}/has-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_BILLING_READ.name())")
    public ResponseEntity<Boolean> hasActive(@PathVariable Long accountId) {
        return ResponseEntity.ok(tenantBillingService.hasActivePayment(accountId));
    }
}

