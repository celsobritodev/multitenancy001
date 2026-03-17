package brito.com.multitenancy001.tenant.subscription.api.dto;

import java.math.BigDecimal;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de mudança efetiva de plano no contexto do Tenant.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Para downgrade elegível, os campos de billing podem ficar nulos.</li>
 *   <li>Para upgrade, o próprio tenant informa os metadados necessários para cobrança.</li>
 *   <li>Enquanto não houver catálogo oficial de preços, o valor continua vindo pelo contrato HTTP.</li>
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
public record TenantPlanChangeRequest(
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