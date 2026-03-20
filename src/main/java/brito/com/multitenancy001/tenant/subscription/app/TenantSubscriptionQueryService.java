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
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangePreviewResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanLimitsResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanViolationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de consulta de subscription no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver a conta atual do tenant autenticado.</li>
 *   <li>Calcular uso e limites do plano corrente.</li>
 *   <li>Executar preview de mudança de plano.</li>
 *   <li>Expor visão consolidada de upgrades e downgrades elegíveis/bloqueados.</li>
 * </ul>
 *
 * <p>Diretriz arquitetural:</p>
 * <ul>
 *   <li>Este service não executa mais crossing TENANT -&gt; PUBLIC diretamente.</li>
 *   <li>A resolução da conta atual fica centralizada em {@link TenantSubscriptionAccountResolver}.</li>
 *   <li>Reduz boundary leak e simplifica manutenção futura.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionQueryService {

    private final TenantSubscriptionAccountResolver tenantSubscriptionAccountResolver;
    private final AccountPlanUsageService accountPlanUsageService;
    private final SubscriptionPlanCatalog subscriptionPlanCatalog;
    private final PlanChangePolicy planChangePolicy;

    /**
     * Retorna a visão consolidada dos limites e do uso atual da conta autenticada.
     *
     * @return resposta de limites do plano atual
     */
    public TenantPlanLimitsResponse getMyLimits() {
        Account account = tenantSubscriptionAccountResolver.resolveCurrentAccount();

        log.info("Consultando limites da assinatura do tenant autenticado. accountId={}", account.getId());

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
            if (candidate == account.getSubscriptionPlan()) {
                continue;
            }

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
                "Limites da assinatura carregados com sucesso. accountId={}, currentPlan={}, currentUsers={}, currentProducts={}, currentStorageMb={}",
                account.getId(),
                account.getSubscriptionPlan(),
                usage.currentUsers(),
                usage.currentProducts(),
                usage.currentStorageMb()
        );

        return response;
    }

    /**
     * Alias semântico para consulta dos limites atuais.
     *
     * @return limites atuais da conta
     */
    public TenantPlanLimitsResponse getCurrentLimits() {
        return getMyLimits();
    }

    /**
     * Executa preview de mudança de plano para a conta atual do tenant.
     *
     * @param targetPlan plano alvo
     * @return preview consolidado da mudança
     */
    public TenantPlanChangePreviewResponse previewChange(SubscriptionPlan targetPlan) {
        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        Account account = tenantSubscriptionAccountResolver.resolveCurrentAccount();

        log.info(
                "Executando preview de mudança de plano no tenant. accountId={}, currentPlan={}, targetPlan={}",
                account.getId(),
                account.getSubscriptionPlan(),
                targetPlan
        );

        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        PlanEligibilityResult result = planChangePolicy.previewChange(usage, targetPlan);

        TenantPlanChangePreviewResponse response = toPreviewResponse(result);

        log.info(
                "Preview de mudança de plano calculado com sucesso. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}",
                account.getId(),
                result.currentPlan(),
                result.targetPlan(),
                result.changeType(),
                result.eligible()
        );

        return response;
    }

    /**
     * Converte o resultado de elegibilidade em DTO de resposta da API tenant.
     *
     * @param result resultado de elegibilidade
     * @return resposta de preview de mudança de plano
     */
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

    /**
     * Converte violação de elegibilidade em DTO da API.
     *
     * @param violation violação do domínio
     * @return resposta de violação
     */
    private TenantPlanViolationResponse toViolationResponse(PlanEligibilityViolation violation) {
        return new TenantPlanViolationResponse(
                violation.type().name(),
                violation.resource(),
                violation.currentValue(),
                violation.allowedValue(),
                violation.message()
        );
    }

    /**
     * Calcula capacidade restante de um recurso.
     *
     * @param currentValue uso atual
     * @param maxValue valor máximo permitido
     * @param unlimited indica se o plano é ilimitado
     * @return restante disponível
     */
    private long calculateRemaining(long currentValue, int maxValue, boolean unlimited) {
        if (unlimited) {
            return Long.MAX_VALUE;
        }

        return Math.max(0L, (long) maxValue - currentValue);
    }

    /**
     * Retorna a lista de planos self-service ordenados por rank comercial.
     *
     * @return lista ordenada de planos
     */
    private List<SubscriptionPlan> orderedCommercialPlans() {
        return List.of(SubscriptionPlan.values()).stream()
                .filter(subscriptionPlanCatalog::isSelfServiceAllowed)
                .sorted(Comparator.comparingInt(subscriptionPlanCatalog::rankOf))
                .toList();
    }
}