package brito.com.multitenancy001.controlplane.api.controller.billing;

import brito.com.multitenancy001.controlplane.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.controlplane.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.controlplane.application.billing.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/billing/payments")
@RequiredArgsConstructor
@Validated
public class ControlPlanePaymentController {

    private final PaymentService paymentService;

    // =========================================================
    // ADMIN / CROSS-TENANT
    // =========================================================

    @PostMapping("/by-account/{accountId}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ') and hasAuthority('CP_BILLING_WRITE')")
    public ResponseEntity<PaymentResponse> processPaymentForAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody AdminPaymentRequest body
    ) {
        // Garante que o accountId do path manda (evita inconsistência)
        AdminPaymentRequest adminPaymentRequest = new AdminPaymentRequest(
                accountId,
                body.amount(),
                body.paymentMethod(),
                body.paymentGateway(),
                body.description()
        );

        PaymentResponse response = paymentService.processPaymentForAccount(adminPaymentRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/by-account/{accountId}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ') and hasAuthority('CP_BILLING_READ')")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByAccountAdmin(@PathVariable Long accountId) {
        return ResponseEntity.ok(paymentService.getPaymentsByAccount(accountId));
    }

    @GetMapping("/by-account/{accountId}/active")
    @PreAuthorize("hasAuthority('CP_TENANT_READ') and hasAuthority('CP_BILLING_READ')")
    public ResponseEntity<Boolean> hasActivePaymentAdmin(@PathVariable Long accountId) {
        return ResponseEntity.ok(paymentService.hasActivePayment(accountId));
    }

    @PostMapping("/{paymentId}/complete-manual")
    @PreAuthorize("hasAuthority('CP_BILLING_WRITE')")
    public ResponseEntity<PaymentResponse> completeManually(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.completePaymentManually(paymentId));
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasAuthority('CP_BILLING_WRITE')")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest refundRequest
    ) {
        PaymentResponse response =
                paymentService.refundPayment(paymentId, refundRequest.amount(), refundRequest.reason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('CP_BILLING_READ')")
    public ResponseEntity<BigDecimal> getRevenue(
            @RequestParam("start")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam("end")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return ResponseEntity.ok(paymentService.getTotalRevenue(startDate, endDate));
    }

    public record RefundRequest(
            @DecimalMin(value = "0.01", message = "amount deve ser > 0 quando informado")
            BigDecimal amount,

            @NotBlank(message = "reason é obrigatório")
            String reason
    ) {}
}
