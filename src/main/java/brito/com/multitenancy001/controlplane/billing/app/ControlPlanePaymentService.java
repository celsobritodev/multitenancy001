package brito.com.multitenancy001.controlplane.billing.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada de aplicação para pagamentos do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Manter compatibilidade com controllers e chamadores atuais.</li>
 *   <li>Expor os casos de uso públicos de pagamento administrativo e self-service.</li>
 *   <li>Delegar o fluxo real para {@link ControlPlanePaymentCommandService}.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta classe deve permanecer fina.</li>
 *   <li>Não deve concentrar regra de negócio, transição de estado,
 *       fila de upgrade ou mapeamento de DTO.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentService {

    private final ControlPlanePaymentCommandService controlPlanePaymentCommandService;

    /**
     * Processa um pagamento administrativo para uma conta explícita.
     *
     * @param adminPaymentRequest request administrativo
     * @return resposta consolidada do pagamento
     */
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        log.info("Delegando pagamento administrativo para command service.");
        return controlPlanePaymentCommandService.processPaymentForAccount(adminPaymentRequest);
    }

    /**
     * Processa um pagamento self-service para a conta autenticada.
     *
     * @param paymentRequest request self-service
     * @return resposta consolidada do pagamento
     */
    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        log.info("Delegando pagamento self-service para command service.");
        return controlPlanePaymentCommandService.processPaymentForMyAccount(paymentRequest);
    }
}