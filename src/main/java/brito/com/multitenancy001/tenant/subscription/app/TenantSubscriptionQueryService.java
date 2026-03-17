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
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangePreviewResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanLimitsResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanViolationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service de consulta da assinatura no contexto do Tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver a conta atual a partir da identidade autenticada do request.</li>
 *   <li>Consultar plano atual, uso atual e limites vigentes.</li>
 *   <li>Montar visão consolidada de limites, saldo restante e possibilidades de mudança.</li>
 *   <li>Executar preview de mudança sem side effects.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Não acessa controller.</li>
 *   <li>Não acessa tenant repository diretamente.</li>
 *   <li>Opera no application layer usando o núcleo já pronto de subscription.</li>
 *   <li>Usa PublicSchemaUnitOfWork para leitura consistente no public schema.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionQueryService {

    private final TenantRequestIdentityService requestIdentity;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final SubscriptionPlanCatalog subscriptionPlanCatalog;
    private final PlanChangePolicy planChangePolicy;

    /**
     * Retorna a visão consolidada de limites/uso da conta autenticada.
     *
     * <p>Este método é o contrato principal usado pelo endpoint:</p>
     * <ul>
     *   <li>GET /api/tenant/subscription/me/limits</li>
     * </ul>
     *
     * @return response consolidado de limites/uso
     */
    public TenantPlanLimitsResponse getMyLimits() {
        Long accountId = requireCurrentAccountId();

        log.info("Consultando limites da assinatura do tenant autenticado. accountId={}", accountId);

        return publicSchemaUnitOfWork.readOnly(() -> {
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
                    "Limites da assinatura do tenant carregados com sucesso. accountId={}, currentPlan={}, currentUsers={}, currentProducts={}, currentStorageMb={}",
                    account.getId(),
                    account.getSubscriptionPlan(),
                    usage.currentUsers(),
                    usage.currentProducts(),
                    usage.currentStorageMb()
            );

            return response;
        });
    }

    /**
     * Alias de compatibilidade para chamadas antigas.
     *
     * <p>Mantido para evitar quebra em pontos do sistema que ainda usem o contrato
     * anterior getCurrentLimits(). Internamente delega para getMyLimits().</p>
     *
     * @return response consolidado de limites/uso
     */
    public TenantPlanLimitsResponse getCurrentLimits() {
        return getMyLimits();
    }

    /**
     * Executa preview de mudança de plano da conta autenticada.
     *
     * @param targetPlan plano alvo
     * @return preview completo
     */
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

        return publicSchemaUnitOfWork.readOnly(() -> {
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
        });
    }

    /**
     * Resolve e valida o accountId da identidade autenticada.
     *
     * @return accountId atual
     */
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

    /**
     * Carrega a conta ativa/não deletada do tenant autenticado.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    private Account loadAccount(Long accountId) {
        return accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada para o tenant autenticado",
                        404
                ));
    }

    /**
     * Converte o resultado de preview do núcleo para o DTO HTTP do tenant.
     *
     * @param result resultado do núcleo de elegibilidade
     * @return response de preview
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
     * Converte uma violação do núcleo para o DTO HTTP.
     *
     * @param violation violação de elegibilidade
     * @return response de violação
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
     * Calcula saldo restante para um recurso.
     *
     * @param currentValue uso atual
     * @param maxValue limite do plano
     * @param unlimited indica se o plano é ilimitado
     * @return saldo restante
     */
    private long calculateRemaining(long currentValue, int maxValue, boolean unlimited) {
        if (unlimited) {
            return Long.MAX_VALUE;
        }

        return Math.max(0L, (long) maxValue - currentValue);
    }

    /**
     * Retorna os planos comerciais ordenados por ranking.
     *
     * @return lista ordenada de planos self-service permitidos
     */
    private List<SubscriptionPlan> orderedCommercialPlans() {
        return List.of(SubscriptionPlan.values()).stream()
                .filter(subscriptionPlanCatalog::isSelfServiceAllowed)
                .sorted(Comparator.comparingInt(subscriptionPlanCatalog::rankOf))
                .toList();
    }
}