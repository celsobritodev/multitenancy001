package brito.com.multitenancy001.controlplane.accounts.api.subscription.dto;

import java.math.BigDecimal;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de mudança efetiva de plano no Control Plane.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Para downgrade elegível, o billing não é usado e os campos financeiros podem ficar nulos.</li>
 *   <li>Para upgrade, este request já carrega os dados mínimos para iniciar/processar o billing.</li>
 *   <li>O preço ainda é informado pela API enquanto não existir catálogo central de preços.</li>
 * </ul>
 *
 * @param targetPlan plano alvo
 * @param billingCycle ciclo de cobrança para upgrade
 * @param paymentMethod método de pagamento para upgrade
 * @param paymentGateway gateway de pagamento para upgrade
 * @param amount valor a cobrar
 * @param planPriceSnapshot snapshot opcional do preço do plano
 * @param currencyCode moeda
 * @param reason motivo funcional
 */
public record AccountPlanChangeRequest(
        @NotNull(message = "targetPlan é obrigatório")
        SubscriptionPlan targetPlan,

        BillingCycle billingCycle,

        PaymentMethod paymentMethod,

        PaymentGateway paymentGateway,

        @DecimalMin(value = "0.01", message = "amount deve ser > 0")
        BigDecimal amount,

        BigDecimal planPriceSnapshot,

        @Size(min = 3, max = 3, message = "currencyCode deve ter exatamente 3 caracteres")
        String currencyCode,

        @Size(max = 500, message = "reason deve ter no máximo 500 caracteres")
        String reason
) {
}