package brito.com.multitenancy001.shared.api.dto.billing;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response de pagamento.
 *
 * @param id id
 * @param accountId conta
 * @param amount valor
 * @param paymentMethod método
 * @param paymentGateway gateway
 * @param paymentStatus status
 * @param description descrição
 * @param targetPlan plano alvo
 * @param billingCycle ciclo
 * @param purpose finalidade
 * @param planPriceSnapshot snapshot de preço
 * @param currencyCode moeda
 * @param effectiveFrom início efetivo
 * @param coverageEndDate fim da cobertura
 * @param paidAt data do pagamento
 * @param validUntil validade
 * @param refundedAt data de reembolso
 * @param createdAt criação
 * @param updatedAt atualização
 */
public record PaymentResponse(
        Long id,
        Long accountId,

        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentGateway paymentGateway,
        PaymentStatus paymentStatus,

        String description,

        SubscriptionPlan targetPlan,
        BillingCycle billingCycle,
        PaymentPurpose purpose,
        BigDecimal planPriceSnapshot,
        String currencyCode,
        Instant effectiveFrom,
        Instant coverageEndDate,

        Instant paidAt,
        Instant validUntil,
        Instant refundedAt,

        Instant createdAt,
        Instant updatedAt
) {
}