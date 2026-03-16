package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Política central de preview/elegibilidade para mudança de plano.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Classificar upgrade / downgrade / no-change</li>
 *   <li>Bloquear planos não elegíveis ao fluxo comercial</li>
 *   <li>Validar incompatibilidades de downgrade com base no uso atual</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Upgrade não é bloqueado pelo uso atual</li>
 *   <li>Downgrade exige compatibilidade com os limites do plano alvo</li>
 *   <li>BUILT_IN_PLAN não participa do fluxo comercial</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanChangePolicy {

    private final SubscriptionPlanCatalog subscriptionPlanCatalog;

    /**
     * Faz o preview completo de elegibilidade de mudança de plano.
     *
     * @param usageSnapshot uso atual da conta
     * @param targetPlan plano alvo
     * @return resultado da análise
     */
    public PlanEligibilityResult previewChange(PlanUsageSnapshot usageSnapshot, SubscriptionPlan targetPlan) {
        if (usageSnapshot == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "usageSnapshot é obrigatório", 400);
        }
        if (usageSnapshot.currentPlan() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentPlan é obrigatório", 400);
        }
        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        SubscriptionPlan currentPlan = usageSnapshot.currentPlan();
        PlanChangeType changeType = subscriptionPlanCatalog.classifyChange(currentPlan, targetPlan);
        PlanLimitSnapshot targetLimits = subscriptionPlanCatalog.resolveLimits(targetPlan);

        List<PlanEligibilityViolation> violations = new ArrayList<>();

        if (!subscriptionPlanCatalog.isSelfServiceAllowed(targetPlan)) {
            violations.add(new PlanEligibilityViolation(
                    PlanEligibilityViolationType.TARGET_PLAN_NOT_ALLOWED,
                    "PLAN",
                    0,
                    0,
                    "Plano alvo não é elegível para self-service."
            ));
        }

        if (subscriptionPlanCatalog.isBuiltIn(currentPlan)) {
            violations.add(new PlanEligibilityViolation(
                    PlanEligibilityViolationType.BUILTIN_PLAN_NOT_ALLOWED,
                    "PLAN",
                    0,
                    0,
                    "Conta built-in não participa do fluxo comercial de assinatura."
            ));
        }

        if (changeType == PlanChangeType.NO_CHANGE) {
            violations.add(new PlanEligibilityViolation(
                    PlanEligibilityViolationType.SAME_PLAN_NOT_ALLOWED,
                    "PLAN",
                    0,
                    0,
                    "A conta já está no plano informado."
            ));
        }

        if (changeType == PlanChangeType.DOWNGRADE && !targetLimits.unlimited()) {
            validateUsers(usageSnapshot, targetLimits, violations);
            validateProducts(usageSnapshot, targetLimits, violations);
            validateStorage(usageSnapshot, targetLimits, violations);
        }

        boolean eligible = violations.isEmpty();

        log.info(
                "Preview de mudança de plano calculado. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}, violations={}",
                usageSnapshot.accountId(),
                currentPlan,
                targetPlan,
                changeType,
                eligible,
                violations.size()
        );

        return new PlanEligibilityResult(
                currentPlan,
                targetPlan,
                changeType,
                eligible,
                usageSnapshot,
                targetLimits,
                List.copyOf(violations)
        );
    }

    /**
     * Exige que a mudança seja elegível.
     *
     * @param usageSnapshot uso atual
     * @param targetPlan plano alvo
     * @return resultado elegível
     */
    public PlanEligibilityResult requireEligibleChange(PlanUsageSnapshot usageSnapshot, SubscriptionPlan targetPlan) {
        PlanEligibilityResult result = previewChange(usageSnapshot, targetPlan);

        if (!result.eligible()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Mudança de plano não elegível", 409);
        }

        return result;
    }

    /**
     * Informa se a mudança é upgrade.
     *
     * @param currentPlan plano atual
     * @param targetPlan plano alvo
     * @return true se upgrade
     */
    public boolean isUpgrade(SubscriptionPlan currentPlan, SubscriptionPlan targetPlan) {
        return subscriptionPlanCatalog.classifyChange(currentPlan, targetPlan) == PlanChangeType.UPGRADE;
    }

    /**
     * Informa se a mudança é downgrade.
     *
     * @param currentPlan plano atual
     * @param targetPlan plano alvo
     * @return true se downgrade
     */
    public boolean isDowngrade(SubscriptionPlan currentPlan, SubscriptionPlan targetPlan) {
        return subscriptionPlanCatalog.classifyChange(currentPlan, targetPlan) == PlanChangeType.DOWNGRADE;
    }

    /**
     * Valida incompatibilidade de usuários.
     *
     * @param usage uso atual
     * @param limits limites do plano alvo
     * @param violations coleção de violações
     */
    private void validateUsers(
            PlanUsageSnapshot usage,
            PlanLimitSnapshot limits,
            List<PlanEligibilityViolation> violations
    ) {
        if (usage.currentUsers() > limits.maxUsers()) {
            violations.add(new PlanEligibilityViolation(
                    PlanEligibilityViolationType.USERS_LIMIT_EXCEEDED,
                    "USERS",
                    usage.currentUsers(),
                    limits.maxUsers(),
                    "Reduza a quantidade de usuários ativos antes de mudar para o plano " + limits.plan() + "."
            ));
        }
    }

    /**
     * Valida incompatibilidade de produtos.
     *
     * @param usage uso atual
     * @param limits limites do plano alvo
     * @param violations coleção de violações
     */
    private void validateProducts(
            PlanUsageSnapshot usage,
            PlanLimitSnapshot limits,
            List<PlanEligibilityViolation> violations
    ) {
        if (usage.currentProducts() > limits.maxProducts()) {
            violations.add(new PlanEligibilityViolation(
                    PlanEligibilityViolationType.PRODUCTS_LIMIT_EXCEEDED,
                    "PRODUCTS",
                    usage.currentProducts(),
                    limits.maxProducts(),
                    "Reduza a quantidade de produtos antes de mudar para o plano " + limits.plan() + "."
            ));
        }
    }

    /**
     * Valida incompatibilidade de storage.
     *
     * @param usage uso atual
     * @param limits limites do plano alvo
     * @param violations coleção de violações
     */
    private void validateStorage(
            PlanUsageSnapshot usage,
            PlanLimitSnapshot limits,
            List<PlanEligibilityViolation> violations
    ) {
        if (usage.currentStorageMb() > limits.maxStorageMb()) {
            violations.add(new PlanEligibilityViolation(
                    PlanEligibilityViolationType.STORAGE_LIMIT_EXCEEDED,
                    "STORAGE_MB",
                    usage.currentStorageMb(),
                    limits.maxStorageMb(),
                    "Libere armazenamento antes de mudar para o plano " + limits.plan() + "."
            ));
        }
    }
}