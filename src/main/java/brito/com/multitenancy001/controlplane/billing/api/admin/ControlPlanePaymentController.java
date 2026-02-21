package brito.com.multitenancy001.controlplane.billing.api.admin;

import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentService;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller ADMIN (Control Plane) para operações de Billing/Payments.
 *
 * Regras:
 * - Controller NÃO acessa repository.
 * - Payload deve ser validado com @Valid (ControllerComplianceVerifier).
 * - Controller delega para Application Service.
 */
@RestController
@RequestMapping("/api/controlplane/billing/payments")
@RequiredArgsConstructor
public class ControlPlanePaymentController {

    private final ControlPlanePaymentService controlPlanePaymentService;

    /**
     * Admin força/registrar um pagamento para uma conta.
     */
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_BILLING_WRITE.asAuthority())")
    public ResponseEntity<PaymentResponse> create(
            /* Valida o payload (@Valid é obrigatório pelo compliance). */
            @Valid @RequestBody AdminPaymentRequest request
    ) {
        return ResponseEntity.ok(controlPlanePaymentService.processPaymentForAccount(request));
    }
}