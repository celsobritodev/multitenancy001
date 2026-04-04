package brito.com.multitenancy001.controlplane.billing.app;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;

/**
 * Mapper responsável por converter {@link Payment} em {@link PaymentResponse}.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Isolar o mapeamento domínio → DTO fora do fluxo principal de comando.</li>
 *   <li>Padronizar o shape de resposta do módulo de billing.</li>
 * </ul>
 */
@Component
public class ControlPlanePaymentApiResponseMapper {

    /**
     * Converte pagamento de domínio para DTO de resposta.
     *
     * <p>Observação:
     * o domínio atual não possui um campo {@code paidAt} separado;
     * por isso o DTO continua sendo preenchido com {@code paymentDate}
     * no ponto correspondente do contrato atual.</p>
     *
     * @param payment pagamento de domínio
     * @return DTO de resposta
     */
    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccount() != null ? payment.getAccount().getId() : null,

                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getStatus(),

                payment.getDescription(),

                payment.getTargetPlan(),
                payment.getBillingCycle(),
                payment.getPaymentPurpose(),
                payment.getPlanPriceSnapshot(),
                payment.getCurrency(),
                payment.getEffectiveFrom(),
                payment.getCoverageEndDate(),

                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getRefundedAt(),

                payment.getAudit() != null ? payment.getAudit().getCreatedAt() : null,
                payment.getAudit() != null ? payment.getAudit().getUpdatedAt() : null
        );
    }
}