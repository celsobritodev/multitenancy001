package brito.com.multitenancy001.tenant.api.controller.billing;

import brito.com.multitenancy001.controlplane.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.controlplane.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.controlplane.application.billing.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/billing/payments")
@RequiredArgsConstructor
public class TenantPaymentController {

    private final PaymentService paymentService;

    // =========================
    // MY TENANT (SAFE DEFAULT)
    // =========================

    @PostMapping
    @PreAuthorize("hasAuthority('TEN_BILLING_WRITE')")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        PaymentResponse response = paymentService.processPaymentForMyAccount(paymentRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('TEN_BILLING_READ')")
    public ResponseEntity<PaymentResponse> getById(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentByIdForMyAccount(paymentId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TEN_BILLING_READ')")
    public ResponseEntity<List<PaymentResponse>> getMyPayments() {
        return ResponseEntity.ok(paymentService.getPaymentsByMyAccount());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_BILLING_READ')")
    public ResponseEntity<Boolean> hasActivePaymentMyAccount() {
        return ResponseEntity.ok(paymentService.hasActivePaymentMyAccount());
    }
}
