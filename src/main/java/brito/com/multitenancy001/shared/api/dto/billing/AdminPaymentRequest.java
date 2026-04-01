package brito.com.multitenancy001.shared.api.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request administrativo de criação/processamento de pagamento.
 *
 * <p>Este request suporta billing binding com subscription e chave opcional
 * de idempotência para retry-safe.</p>
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
 * @param idempotencyKey chave opcional de idempotência
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
        Instant coverageEndDate,

        @Size(max = 160, message = "idempotencyKey deve ter no máximo 160 caracteres")
        String idempotencyKey
) {
}