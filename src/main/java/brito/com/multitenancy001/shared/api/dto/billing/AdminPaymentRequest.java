package brito.com.multitenancy001.shared.api.dto.billing;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request administrativo de criação/processamento de pagamento.
 *
 * @param accountId conta alvo
 * @param amount valor
 * @param paymentMethod método
 * @param paymentGateway gateway
 * @param description descrição
 * @param targetPlan plano alvo vinculado
 * @param billingCycle ciclo de cobrança
 * @param purpose finalidade
 * @param planPriceSnapshot snapshot de preço
 * @param currencyCode moeda
 * @param effectiveFrom início efetivo
 * @param coverageEndDate fim da cobertura
 */
public record AdminPaymentRequest(
        @NotNull
        Long accountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount deve ser > 0")
        BigDecimal amount,

        @NotNull
        PaymentMethod paymentMethod,

        @NotNull
        PaymentGateway paymentGateway,

        @Size(max = 500, message = "description deve ter no máximo 500 caracteres")
        String description,

        SubscriptionPlan targetPlan,
        BillingCycle billingCycle,
        PaymentPurpose purpose,
        BigDecimal planPriceSnapshot,

        @Size(min = 3, max = 3, message = "currencyCode deve ter exatamente 3 caracteres")
        String currencyCode,

        Instant effectiveFrom,
        Instant coverageEndDate
) {
}