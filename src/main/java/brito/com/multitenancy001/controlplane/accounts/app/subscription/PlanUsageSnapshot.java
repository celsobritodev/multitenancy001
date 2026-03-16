package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;

/**
 * Snapshot imutável do uso atual da conta.
 *
 * @param accountId id da conta
 * @param currentPlan plano atual
 * @param currentUsers quantidade de usuários atuais
 * @param currentProducts quantidade de produtos atuais
 * @param currentStorageMb storage consumido atual
 */
public record PlanUsageSnapshot(
        Long accountId,
        SubscriptionPlan currentPlan,
        long currentUsers,
        long currentProducts,
        long currentStorageMb
) {
}