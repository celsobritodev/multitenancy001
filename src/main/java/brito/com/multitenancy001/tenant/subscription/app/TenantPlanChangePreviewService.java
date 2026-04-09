package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangePolicy;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.app.dto.TenantUsageMeasurement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo preview da mudança de plano no self-service do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Carregar a conta no public schema via crossing explícito.</li>
 *   <li>Calcular usage snapshot no próprio contexto tenant.</li>
 *   <li>Executar preview de elegibilidade da troca de plano.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPlanChangePreviewService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final AccountRepository accountRepository;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final PlanChangePolicy planChangePolicy;

    /**
     * Carrega a conta no public schema através de crossing explícito.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    public Account loadAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Account account = tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() ->
                        accountRepository.findByIdAndDeletedFalse(accountId)
                                .orElseThrow(() -> new ApiException(
                                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                                        "Conta não encontrada",
                                        404
                                ))
                )
        );

        log.info(
                "Conta carregada para orchestration tenant. accountId={}, currentPlan={}, status={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getStatus()
        );

        return account;
    }

    /**
     * Calcula preview de elegibilidade.
     *
     * @param account conta alvo
     * @param targetPlan plano alvo
     * @return preview de elegibilidade
     */
    public PlanEligibilityResult preview(Account account, SubscriptionPlan targetPlan) {
        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        PlanUsageSnapshot usage = resolveUsageSnapshot(account);
        return planChangePolicy.previewChange(usage, targetPlan);
    }

    /**
     * Resolve usage snapshot localmente no tenant,
     * sem depender do service do control plane.
     *
     * @param account conta alvo
     * @return snapshot de uso
     */
    private PlanUsageSnapshot resolveUsageSnapshot(Account account) {
        validateAccountForTenantUsage(account);

        if (account.isBuiltInAccount()) {
            log.info(
                    "Conta built-in detectada no preview tenant. accountId={}, currentPlan={}",
                    account.getId(),
                    account.getSubscriptionPlan()
            );

            return new PlanUsageSnapshot(
                    account.getId(),
                    account.getSubscriptionPlan(),
                    0L,
                    0L,
                    0L
            );
        }

        TenantUsageMeasurement measurement = tenantQuotaEnforcementService.measureUsage(
                account.getId(),
                account.getTenantSchema()
        );

        PlanUsageSnapshot usage = new PlanUsageSnapshot(
                account.getId(),
                account.getSubscriptionPlan(),
                measurement.currentUsers(),
                measurement.currentProducts(),
                0L
        );

        log.info(
                "Usage snapshot tenant calculado para preview. accountId={}, tenantSchema={}, currentPlan={}, currentUsers={}, currentProducts={}, currentStorageMb={}",
                account.getId(),
                account.getTenantSchema(),
                account.getSubscriptionPlan(),
                usage.currentUsers(),
                usage.currentProducts(),
                usage.currentStorageMb()
        );

        return usage;
    }

    /**
     * Valida se a conta possui os dados mínimos para cálculo de uso no contexto tenant.
     *
     * @param account conta resolvida
     */
    private void validateAccountForTenantUsage(Account account) {
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória", 400);
        }
        if (account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        if (account.getSubscriptionPlan() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "subscriptionPlan é obrigatório", 400);
        }
        if (!account.isBuiltInAccount() && !StringUtils.hasText(account.getTenantSchema())) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }
    }
}