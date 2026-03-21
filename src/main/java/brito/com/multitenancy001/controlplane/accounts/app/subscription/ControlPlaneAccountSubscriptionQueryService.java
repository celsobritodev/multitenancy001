package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

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

/**
 * Query service do Control Plane para assinatura, plano e limites de contas.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Consultar plano atual e status da conta.</li>
 *   <li>Consultar uso real e limites atuais.</li>
 *   <li>Executar preview de mudança de plano por accountId.</li>
 *   <li>Garantir resposta administrativa consistente com os cálculos do tenant.</li>
 * </ul>
 *
 * <p><b>Regras arquiteturais críticas:</b></p>
 * <ul>
 *   <li>A leitura da conta ocorre no PUBLIC schema.</li>
 *   <li>O cálculo de uso não pode ocorrer dentro do mesmo bloco do PUBLIC UoW,
 *       pois o cálculo de usage entra no tenant schema.</li>
 *   <li>Evitar bind de tenant dentro de transação PUBLIC já ativa.</li>
 *   <li>O remaining deve ser clampado para nunca retornar valor negativo.</li>
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
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Consultando assinatura da conta no control plane. accountId={}", accountId);

        Account account = publicSchemaUnitOfWork.readOnly(() -> loadAccount(accountId));

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

        AccountSubscriptionAdminResponse response = new AccountSubscriptionAdminResponse(
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
                "Assinatura da conta consultada com sucesso no control plane. accountId={}, currentPlan={}, currentUsers={}, currentProducts={}, currentStorageMb={}, remainingUsers={}, remainingProducts={}, remainingStorageMb={}",
                account.getId(),
                account.getSubscriptionPlan(),
                usage.currentUsers(),
                usage.currentProducts(),
                usage.currentStorageMb(),
                remainingUsers,
                remainingProducts,
                remainingStorageMb
        );

        return response;
    }

    /**
     * Executa preview de mudança de plano para a conta.
     *
     * @param accountId id da conta
     * @param targetPlan plano alvo
     * @return preview completo
     */
    public AccountPlanChangePreviewResponse previewChange(Long accountId, SubscriptionPlan targetPlan) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        log.info(
                "Executando preview de mudança de plano no control plane. accountId={}, targetPlan={}",
                accountId,
                targetPlan
        );

        Account account = publicSchemaUnitOfWork.readOnly(() -> loadAccount(accountId));

        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        PlanEligibilityResult result = planChangePolicy.previewChange(usage, targetPlan);

        AccountPlanChangePreviewResponse response = new AccountPlanChangePreviewResponse(
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
                result.violations().stream()
                        .map(this::toViolationResponse)
                        .toList()
        );

        log.info(
                "Preview de mudança de plano calculado com sucesso no control plane. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}, violations={}",
                accountId,
                result.currentPlan(),
                result.targetPlan(),
                result.changeType(),
                result.eligible(),
                result.violations().size()
        );

        return response;
    }

    /**
     * Carrega a conta ativa e não deletada.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    private Account loadAccount(Long accountId) {
        return accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada",
                        404
                ));
    }

    /**
     * Converte violação de elegibilidade em DTO administrativo.
     *
     * @param violation violação do domínio
     * @return DTO de violação
     */
    private AccountPlanViolationResponse toViolationResponse(PlanEligibilityViolation violation) {
        return new AccountPlanViolationResponse(
                violation.type().name(),
                violation.resource(),
                violation.currentValue(),
                violation.allowedValue(),
                violation.message()
        );
    }

    /**
     * Calcula o saldo remanescente de um recurso do plano.
     *
     * <p><b>Regras:</b></p>
     * <ul>
     *   <li>Se o plano for ilimitado, retorna {@link Long#MAX_VALUE}.</li>
     *   <li>Se o plano for limitado, nunca retorna valor negativo.</li>
     *   <li>Quando o uso real atinge ou ultrapassa o limite, o retorno é 0.</li>
     * </ul>
     *
     * @param currentUsage uso atual real
     * @param maxAllowed limite máximo do plano
     * @param unlimited indica se o plano é ilimitado
     * @return saldo remanescente normalizado
     */
    private long calculateRemaining(long currentUsage, long maxAllowed, boolean unlimited) {
        if (unlimited) {
            return Long.MAX_VALUE;
        }

        long rawRemaining = maxAllowed - currentUsage;
        long clampedRemaining = Math.max(0L, rawRemaining);

        if (rawRemaining < 0L) {
            log.warn(
                    "Remaining negativo detectado e clampado para zero no control plane. currentUsage={}, maxAllowed={}, rawRemaining={}",
                    currentUsage,
                    maxAllowed,
                    rawRemaining
            );
        }

        return clampedRemaining;
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