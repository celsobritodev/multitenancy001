package brito.com.multitenancy001.controlplane.billing.api.admin;

import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentService;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
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
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/billing/payments")
@RequiredArgsConstructor
@Validated
public class ControlPlanePaymentController {

    private final ControlPlanePaymentService controlPlanePaymentService;

    // Cria/processa um pagamento manual para uma conta (cross-tenant) e aplica efeitos no billing da conta.
    @PostMapping("/by-account/{accountId}")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_WRITE.asAuthority())"
    )
    public ResponseEntity<PaymentResponse> processPaymentForAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody AdminPaymentRequest body
    ) {
        AdminPaymentRequest adminPaymentRequest = new AdminPaymentRequest(
                accountId,
                body.amount(),
                body.paymentMethod(),
                body.paymentGateway(),
                body.description()
        );

        PaymentResponse response = controlPlanePaymentService.processPaymentForAccount(adminPaymentRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lista pagamentos de uma conta (cross-tenant).
    @GetMapping("/by-account/{accountId}")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())"
    )
    public ResponseEntity<List<PaymentResponse>> getPaymentsByAccountAdmin(@PathVariable Long accountId) {
        return ResponseEntity.ok(controlPlanePaymentService.getPaymentsByAccount(accountId));
    }

    // Informa se existe um pagamento COMPLETED vigente para a conta (cross-tenant).
    @GetMapping("/by-account/{accountId}/active")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())"
    )
    public ResponseEntity<Boolean> hasCurrentPaymentAdmin(@PathVariable Long accountId) {
        return ResponseEntity.ok(controlPlanePaymentService.hasActivePayment(accountId));
    }

    // Verifica se um paymentId pertence a um accountId (cross-tenant).
    @GetMapping("/by-account/{accountId}/exists/{paymentId}")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())"
    )
    public ResponseEntity<Boolean> existsByIdAndAccountId(
            @PathVariable Long accountId,
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(controlPlanePaymentService.paymentExistsForAccount(paymentId, accountId));
    }

    // Lista pagamentos COMPLETED de uma conta, ordenados por data de pagamento (cross-tenant).
    @GetMapping("/by-account/{accountId}/completed")
    @PreAuthorize(
            "hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())"
                    + " and hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())"
    )
    public ResponseEntity<List<PaymentResponse>> getCompletedPaymentsByAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(controlPlanePaymentService.getCompletedPaymentsByAccount(accountId));
    }

    // Lista pagamentos dentro de um período (admin global).
    @GetMapping("/period")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())")
    public ResponseEntity<List<PaymentResponse>> listPaymentsInPeriod(
            @RequestParam("start")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam("end")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(controlPlanePaymentService.getPaymentsInPeriod(startDate, endDate));
    }

    // Soma a receita (pagamentos COMPLETED) no período informado (admin global).
    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_READ.asAuthority())")
    public ResponseEntity<BigDecimal> getRevenue(
            @RequestParam("start")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam("end")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(controlPlanePaymentService.getTotalRevenue(startDate, endDate));
    }

    // Marca um pagamento PENDING como COMPLETED manualmente (admin global) e aplica efeitos na conta.
    @PostMapping("/{paymentId}/complete-manual")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_WRITE.asAuthority())")
    public ResponseEntity<PaymentResponse> completeManually(@PathVariable Long paymentId) {
        return ResponseEntity.ok(controlPlanePaymentService.completePaymentManually(paymentId));
    }

    // Reembolsa um pagamento elegível (total ou parcial) registrando o motivo (admin global).
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_WRITE.asAuthority())")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest refundRequest
    ) {
        PaymentResponse response =
                controlPlanePaymentService.refundPayment(paymentId, refundRequest.amount(), refundRequest.reason());
        return ResponseEntity.ok(response);
    }

    public record RefundRequest(
            @DecimalMin(value = "0.01", message = "amount deve ser > 0 quando informado")
            BigDecimal amount,
            @NotBlank(message = "reason é obrigatório")
            String reason
    ) {}
}
