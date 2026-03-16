package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;

/**
 * Snapshot imutável dos limites do plano alvo.
 *
 * @param plan plano
 * @param maxUsers limite de usuários
 * @param maxProducts limite de produtos
 * @param maxStorageMb limite de storage em MB
 * @param unlimited indica se o plano é ilimitado
 */
public record PlanLimitSnapshot(
        SubscriptionPlan plan,
        int maxUsers,
        int maxProducts,
        int maxStorageMb,
        boolean unlimited
) {

    /**
     * Cria snapshot ilimitado.
     *
     * @param plan plano
     * @return snapshot
     */
    public static PlanLimitSnapshot unlimited(SubscriptionPlan plan) {
        return new PlanLimitSnapshot(
                plan,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                true
        );
    }

    /**
     * Cria snapshot limitado.
     *
     * @param plan plano
     * @param maxUsers limite de usuários
     * @param maxProducts limite de produtos
     * @param maxStorageMb limite de storage
     * @return snapshot
     */
    public static PlanLimitSnapshot limited(
            SubscriptionPlan plan,
            int maxUsers,
            int maxProducts,
            int maxStorageMb
    ) {
        return new PlanLimitSnapshot(plan, maxUsers, maxProducts, maxStorageMb, false);
    }
}