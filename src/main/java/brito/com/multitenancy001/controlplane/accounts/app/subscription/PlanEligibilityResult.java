package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;

import java.util.List;

/**
 * Resultado de preview/elegibilidade da mudança de plano.
 *
 * @param currentPlan plano atual
 * @param targetPlan plano alvo
 * @param changeType tipo da mudança
 * @param eligible indica se é elegível
 * @param currentUsage uso atual
 * @param targetLimits limites do plano alvo
 * @param violations violações encontradas
 */
public record PlanEligibilityResult(
        SubscriptionPlan currentPlan,
        SubscriptionPlan targetPlan,
        PlanChangeType changeType,
        boolean eligible,
        PlanUsageSnapshot currentUsage,
        PlanLimitSnapshot targetLimits,
        List<PlanEligibilityViolation> violations
) {

    /**
     * Indica se há violações.
     *
     * @return true se houver
     */
    public boolean hasViolations() {
        return violations != null && !violations.isEmpty();
    }
}