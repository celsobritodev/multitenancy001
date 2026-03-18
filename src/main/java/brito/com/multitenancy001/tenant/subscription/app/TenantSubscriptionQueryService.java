package brito.com.multitenancy001.tenant.subscription.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanUsageService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangePolicy;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangeType;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityViolation;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanLimitSnapshot;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.SubscriptionPlanCatalog;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangePreviewResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanLimitsResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanViolationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionQueryService {

    private final TenantRequestIdentityService requestIdentity;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final SubscriptionPlanCatalog subscriptionPlanCatalog;
    private final PlanChangePolicy planChangePolicy;

    public TenantPlanLimitsResponse getMyLimits() {
        Long accountId = requireCurrentAccountId();

        log.info("Consultando limites da assinatura do tenant autenticado. accountId={}", accountId);

        Account account = loadAccount(accountId);
        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);

        PlanLimitSnapshot limits = subscriptionPlanCatalog.resolveLimits(account.getSubscriptionPlan());

        long remainingUsers = calculateRemaining(
                usage.currentUsers(),
                limits.maxUsers(),
                limits.unlimited()
        );

        long remainingProducts = calculateRemaining(
                usage.currentProducts(),
                limits.maxProducts(),
                limits.unlimited()
        );

        long remainingStorageMb = calculateRemaining(
                usage.currentStorageMb(),
                limits.maxStorageMb(),
                limits.unlimited()
        );

        List<String> eligibleDowngrades = new ArrayList<>();
        List<String> blockedDowngrades = new ArrayList<>();
        List<String> availableUpgrades = new ArrayList<>();

        for (SubscriptionPlan candidate : orderedCommercialPlans()) {
            if (candidate == account.getSubscriptionPlan()) continue;

            PlanChangeType changeType = subscriptionPlanCatalog.classifyChange(
                    account.getSubscriptionPlan(),
                    candidate
            );

            PlanEligibilityResult preview = planChangePolicy.previewChange(usage, candidate);

            if (changeType == PlanChangeType.UPGRADE) {
                availableUpgrades.add(candidate.name());
                continue;
            }

            if (changeType == PlanChangeType.DOWNGRADE) {
                if (preview.eligible()) {
                    eligibleDowngrades.add(candidate.name());
                } else {
                    blockedDowngrades.add(candidate.name());
                }
            }
        }

        TenantPlanLimitsResponse response = new TenantPlanLimitsResponse(
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

        log.info(
                "Limites da assinatura do tenant carregados com sucesso. accountId={}, currentPlan={}, currentUsers={}, currentProducts={}, currentStorageMb={}",
                account.getId(),
                account.getSubscriptionPlan(),
                usage.currentUsers(),
                usage.currentProducts(),
                usage.currentStorageMb()
        );

        return response;
    }

    public TenantPlanLimitsResponse getCurrentLimits() {
        return getMyLimits();
    }

    public TenantPlanChangePreviewResponse previewChange(SubscriptionPlan targetPlan) {
        Long accountId = requireCurrentAccountId();

        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        log.info(
                "Executando preview de mudança de plano no tenant. accountId={}, targetPlan={}",
                accountId,
                targetPlan
        );

        Account account = loadAccount(accountId);
        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);

        PlanEligibilityResult result = planChangePolicy.previewChange(usage, targetPlan);

        TenantPlanChangePreviewResponse response = toPreviewResponse(result);

        log.info(
                "Preview de mudança de plano calculado com sucesso no tenant. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}",
                accountId,
                result.currentPlan(),
                result.targetPlan(),
                result.changeType(),
                result.eligible()
        );

        return response;
    }

    private Long requireCurrentAccountId() {
        Long accountId = requestIdentity.getCurrentAccountId();

        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_REQUIRED,
                    "Não foi possível resolver a conta do tenant autenticado",
                    400
            );
        }

        return accountId;
    }

    private Account loadAccount(Long accountId) {
        return tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() ->
                        accountRepository.findByIdAndDeletedFalse(accountId)
                                .orElseThrow(() -> new ApiException(
                                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                                        "Conta não encontrada para o tenant autenticado",
                                        404
                                ))
                )
        );
    }

    private TenantPlanChangePreviewResponse toPreviewResponse(PlanEligibilityResult result) {
        List<TenantPlanViolationResponse> violations = result.violations().stream()
                .map(this::toViolationResponse)
                .toList();

        return new TenantPlanChangePreviewResponse(
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
                violations
        );
    }

    private TenantPlanViolationResponse toViolationResponse(PlanEligibilityViolation violation) {
        return new TenantPlanViolationResponse(
                violation.type().name(),
                violation.resource(),
                violation.currentValue(),
                violation.allowedValue(),
                violation.message()
        );
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