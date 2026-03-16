package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;

/**
 * Resultado da aplicação de mudança de plano.
 *
 * @param accountId id da conta
 * @param oldPlan plano anterior
 * @param newPlan novo plano efetivado
 * @param changeType tipo da mudança
 * @param eligibility resultado de elegibilidade que fundamentou a operação
 */
public record AccountPlanChangeResult(
        Long accountId,
        SubscriptionPlan oldPlan,
        SubscriptionPlan newPlan,
        PlanChangeType changeType,
        PlanEligibilityResult eligibility
) {
}