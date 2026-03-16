package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.app.DefaultEntitlements;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Catálogo interno de planos.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver limites efetivos por plano usando a configuração oficial do projeto</li>
 *   <li>Classificar upgrade / downgrade / no-change por ranking</li>
 *   <li>Definir se um plano participa ou não do fluxo comercial/self-service</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>FREE, PRO e ENTERPRISE usam {@link DefaultEntitlements}</li>
 *   <li>BUILT_IN_PLAN é tratado como ilimitado e fora do fluxo comercial</li>
 *   <li>Este serviço não conhece billing nem persistência</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanCatalog {

    private final DefaultEntitlements defaultEntitlements;

    /**
     * Resolve os limites efetivos do plano informado.
     *
     * <p>Os valores retornados já respeitam o {@code devMultiplier}
     * porque essa regra já é aplicada dentro de {@link DefaultEntitlements}.</p>
     *
     * @param plan plano
     * @return snapshot dos limites efetivos
     */
    public PlanLimitSnapshot resolveLimits(SubscriptionPlan plan) {
        validatePlanRequired(plan);

        if (plan == SubscriptionPlan.BUILT_IN_PLAN) {
            log.debug("Plano built-in resolvido como ilimitado. plan={}", plan);
            return PlanLimitSnapshot.unlimited(plan);
        }

        PlanLimitSnapshot snapshot = PlanLimitSnapshot.limited(
                plan,
                defaultEntitlements.maxUsers(plan),
                defaultEntitlements.maxProducts(plan),
                defaultEntitlements.maxStorageMb(plan)
        );

        log.debug(
                "Limites resolvidos com sucesso. plan={}, maxUsers={}, maxProducts={}, maxStorageMb={}, unlimited={}",
                snapshot.plan(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb(),
                snapshot.unlimited()
        );

        return snapshot;
    }

    /**
     * Informa se o plano pode participar de fluxo comercial/self-service.
     *
     * @param plan plano
     * @return true se permitido
     */
    public boolean isSelfServiceAllowed(SubscriptionPlan plan) {
        validatePlanRequired(plan);
        return plan != SubscriptionPlan.BUILT_IN_PLAN;
    }

    /**
     * Informa se o plano é interno do sistema.
     *
     * @param plan plano
     * @return true se built-in
     */
    public boolean isBuiltIn(SubscriptionPlan plan) {
        validatePlanRequired(plan);
        return plan == SubscriptionPlan.BUILT_IN_PLAN;
    }

    /**
     * Retorna o ranking relativo do plano para comparação de mudança.
     *
     * <p>Quanto maior o ranking, maior o plano.</p>
     *
     * @param plan plano
     * @return ranking do plano
     */
    public int rankOf(SubscriptionPlan plan) {
        validatePlanRequired(plan);

        return switch (plan) {
            case FREE -> 10;
            case PRO -> 20;
            case ENTERPRISE -> 30;
            case BUILT_IN_PLAN -> 1000;
        };
    }

    /**
     * Classifica a mudança entre plano atual e plano alvo.
     *
     * @param currentPlan plano atual
     * @param targetPlan plano alvo
     * @return tipo da mudança
     */
    public PlanChangeType classifyChange(SubscriptionPlan currentPlan, SubscriptionPlan targetPlan) {
        validatePlanRequired(currentPlan);
        validatePlanRequired(targetPlan);

        int currentRank = rankOf(currentPlan);
        int targetRank = rankOf(targetPlan);

        if (currentRank == targetRank) {
            return PlanChangeType.NO_CHANGE;
        }

        return targetRank > currentRank
                ? PlanChangeType.UPGRADE
                : PlanChangeType.DOWNGRADE;
    }

    /**
     * Valida obrigatoriedade do plano.
     *
     * @param plan plano
     */
    private void validatePlanRequired(SubscriptionPlan plan) {
        if (plan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "subscriptionPlan é obrigatório", 400);
        }
    }
}