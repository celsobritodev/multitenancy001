package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanUsageService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo preview da mudança de plano no self-service do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Carregar a conta no public schema via crossing explícito.</li>
 *   <li>Calcular usage snapshot.</li>
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
    private final AccountPlanUsageService accountPlanUsageService;
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

        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        return planChangePolicy.previewChange(usage, targetPlan);
    }
}