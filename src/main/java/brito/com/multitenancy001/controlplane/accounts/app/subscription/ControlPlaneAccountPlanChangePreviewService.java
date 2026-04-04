package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo preview da mudança de plano no contexto do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Carregar a conta em read-only.</li>
 *   <li>Calcular usage snapshot.</li>
 *   <li>Executar preview de elegibilidade da troca de plano.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountPlanChangePreviewService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final PlanChangePolicy planChangePolicy;

    /**
     * Carrega a conta em modo read-only.
     *
     * @param accountId id da conta
     * @return conta carregada
     */
    public Account loadAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta não encontrada",
                                404
                        ))
        );

        log.info(
                "Conta carregada para orchestration de mudança de plano. accountId={}, currentPlan={}, status={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getStatus()
        );

        return account;
    }

    /**
     * Executa o preview de mudança de plano.
     *
     * @param account conta alvo
     * @param targetPlan plano alvo
     * @return preview calculado
     */
    public PlanEligibilityResult preview(Account account, SubscriptionPlan targetPlan) {
        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        return planChangePolicy.previewChange(usage, targetPlan);
    }
}