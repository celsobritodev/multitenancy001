package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangePreviewResponse;
import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanViolationResponse;
import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountSubscriptionAdminResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Query service do Control Plane para assinatura/plano/limites de contas.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Consultar plano atual e status da conta.</li>
 *   <li>Consultar uso real e limites atuais.</li>
 *   <li>Executar preview de mudança de plano por accountId.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountSubscriptionQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final SubscriptionPlanCatalog subscriptionPlanCatalog;
    private final PlanChangePolicy planChangePolicy;

    /**
     * Retorna a visão consolidada de assinatura da conta.
     *
     * @param accountId id da conta
     * @return response consolidado
     */
    public AccountSubscriptionAdminResponse getSubscription(Long accountId) {
        log.info("Consultando assinatura da conta no control plane. accountId={}", accountId);

        return publicSchemaUnitOfWork.readOnly(() -> {
            Account account = loadAccount(accountId);
            PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
            PlanLimitSnapshot limits = subscriptionPlanCatalog.resolveLimits(account.getSubscriptionPlan());

            long remainingUsers = calculateRemaining(usage.currentUsers(), limits.maxUsers(), limits.unlimited());
            long remainingProducts = calculateRemaining(usage.currentProducts(), limits.maxProducts(), limits.unlimited());
            long remainingStorageMb = calculateRemaining(usage.currentStorageMb(), limits.maxStorageMb(), limits.unlimited());

            List<String> eligibleDowngrades = new ArrayList<>();
            List<String> blockedDowngrades = new ArrayList<>();
            List<String> availableUpgrades = new ArrayList<>();

            for (SubscriptionPlan candidate : orderedCommercialPlans()) {
                if (candidate == account.getSubscriptionPlan()) {
                    continue;
                }

                PlanChangeType changeType = subscriptionPlanCatalog.classifyChange(account.getSubscriptionPlan(), candidate);
                PlanEligibilityResult preview = planChangePolicy.previewChange(usage, candidate);

                if (changeType == PlanChangeType.UPGRADE) {
                    availableUpgrades.add(candidate.name());
                } else if (changeType == PlanChangeType.DOWNGRADE) {
                    if (preview.eligible()) {
                        eligibleDowngrades.add(candidate.name());
                    } else {
                        blockedDowngrades.add(candidate.name());
                    }
                }
            }

            return new AccountSubscriptionAdminResponse(
                    account.getId(),
                    account.getStatus().name(),
                    account.getSubscriptionPlan().name(),
                    limits.maxUsers(),
                    limits.maxProducts(),
                    limits.maxStorageMb(),
                    limits.unlimited(),
                    usage.currentUsers(),
                    usage.currentProducts(),
                    usage.currentStorageMb(),
                    remainingUsers,
                    remainingProducts,
                    remainingStorageMb,
                    List.copyOf(eligibleDowngrades),
                    List.copyOf(blockedDowngrades),
                    List.copyOf(availableUpgrades)
            );
        });
    }

    /**
     * Executa preview de mudança de plano para a conta.
     *
     * @param accountId id da conta
     * @param targetPlan plano alvo
     * @return preview completo
     */
    public AccountPlanChangePreviewResponse previewChange(Long accountId, SubscriptionPlan targetPlan) {
        log.info("Executando preview de mudança de plano no control plane. accountId={}, targetPlan={}", accountId, targetPlan);

        return publicSchemaUnitOfWork.readOnly(() -> {
            Account account = loadAccount(accountId);
            PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
            PlanEligibilityResult result = planChangePolicy.previewChange(usage, targetPlan);

            return new AccountPlanChangePreviewResponse(
                    account.getId(),
                    result.currentPlan().name(),
                    result.targetPlan().name(),
                    result.changeType().name(),
                    result.eligible(),
                    result.currentUsage().currentUsers(),
                    result.currentUsage().currentProducts(),
                    result.currentUsage().currentStorageMb(),
                    result.targetLimits().maxUsers(),
                    result.targetLimits().maxProducts(),
                    result.targetLimits().maxStorageMb(),
                    result.targetLimits().unlimited(),
                    result.violations().stream().map(v -> new AccountPlanViolationResponse(
                            v.type().name(),
                            v.resource(),
                            v.currentValue(),
                            v.allowedValue(),
                            v.message()
                    )).toList()
            );
        });
    }

    private Account loadAccount(Long accountId) {
        return accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));
    }

    private long calculateRemaining(long currentValue, int maxValue, boolean unlimited) {
        if (unlimited) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) maxValue - currentValue);
    }

    private List<SubscriptionPlan> orderedCommercialPlans() {
        return List.of(SubscriptionPlan.values()).stream()
                .filter(subscriptionPlanCatalog::isSelfServiceAllowed)
                .sorted(Comparator.comparingInt(subscriptionPlanCatalog::rankOf))
                .toList();
    }
}