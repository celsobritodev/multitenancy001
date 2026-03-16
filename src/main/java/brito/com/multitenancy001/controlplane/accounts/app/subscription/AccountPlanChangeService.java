package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Caso de uso central de mudança de plano da conta.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar preview completo da mudança</li>
 *   <li>Aplicar downgrade elegível</li>
 *   <li>Aplicar upgrade já aprovado por fluxo interno/billing</li>
 *   <li>Sincronizar snapshot materializado de entitlements após a troca</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Preview sempre passa por {@link PlanChangePolicy}</li>
 *   <li>Downgrade só é aplicado se elegível</li>
 *   <li>Upgrade pode ser aplicado explicitamente por serviço interno</li>
 *   <li>Não há lógica de controller aqui</li>
 *   <li>Não há gateway externo aqui</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPlanChangeService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final PlanChangePolicy planChangePolicy;
    private final AccountEntitlementsSynchronizationService entitlementsSynchronizationService;

    /**
     * Faz preview completo da mudança de plano.
     *
     * @param command comando
     * @return preview de elegibilidade
     */
    public PlanEligibilityResult previewChange(ChangeAccountPlanCommand command) {
        validateCommand(command);

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(command.accountId())
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta não encontrada com id: " + command.accountId(),
                                404
                        ))
        );

        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        PlanEligibilityResult result = planChangePolicy.previewChange(usage, command.targetPlan());

        log.info(
                "Preview de mudança de plano realizado. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}, requestedBy={}, source={}, reason={}",
                command.accountId(),
                result.currentPlan(),
                result.targetPlan(),
                result.changeType(),
                result.eligible(),
                normalize(command.requestedBy()),
                normalize(command.source()),
                normalize(command.reason())
        );

        return result;
    }

    /**
     * Aplica um downgrade imediatamente, desde que elegível.
     *
     * @param command comando
     * @return resultado final
     */
    public AccountPlanChangeResult applyEligibleDowngrade(ChangeAccountPlanCommand command) {
        validateCommand(command);

        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(command.accountId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada com id: " + command.accountId(),
                            404
                    ));

            PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
            PlanEligibilityResult eligibility = planChangePolicy.requireEligibleChange(usage, command.targetPlan());

            if (eligibility.changeType() != PlanChangeType.DOWNGRADE) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Operação não é downgrade elegível", 409);
            }

            return applyPlanChange(account, command, eligibility);
        });
    }

    /**
     * Aplica um upgrade explicitamente.
     *
     * <p>Uso típico:
     * fluxo interno/admin ou chamada feita após aprovação do billing.</p>
     *
     * @param command comando
     * @return resultado final
     */
    public AccountPlanChangeResult applyApprovedUpgrade(ChangeAccountPlanCommand command) {
        validateCommand(command);

        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(command.accountId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada com id: " + command.accountId(),
                            404
                    ));

            PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
            PlanEligibilityResult eligibility = planChangePolicy.requireEligibleChange(usage, command.targetPlan());

            if (eligibility.changeType() != PlanChangeType.UPGRADE) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Operação não é upgrade aprovado", 409);
            }

            return applyPlanChange(account, command, eligibility);
        });
    }

    /**
     * Aplica mudança interna de plano e sincroniza entitlements.
     *
     * @param account conta alvo
     * @param command comando
     * @param eligibility resultado já validado
     * @return resultado final
     */
    private AccountPlanChangeResult applyPlanChange(
            Account account,
            ChangeAccountPlanCommand command,
            PlanEligibilityResult eligibility
    ) {
        SubscriptionPlan oldPlan = account.getSubscriptionPlan();
        SubscriptionPlan newPlan = command.targetPlan();

        account.setSubscriptionPlan(newPlan);
        accountRepository.save(account);

        entitlementsSynchronizationService.synchronizeToCurrentPlan(account);

        log.info(
                "Mudança de plano aplicada com sucesso. accountId={}, changeType={}, oldPlan={}, newPlan={}, requestedBy={}, source={}, reason={}",
                account.getId(),
                eligibility.changeType(),
                oldPlan,
                account.getSubscriptionPlan(),
                normalize(command.requestedBy()),
                normalize(command.source()),
                normalize(command.reason())
        );

        return new AccountPlanChangeResult(
                account.getId(),
                oldPlan,
                account.getSubscriptionPlan(),
                eligibility.changeType(),
                eligibility
        );
    }

    /**
     * Validação base do comando.
     *
     * @param command comando
     */
    private void validateCommand(ChangeAccountPlanCommand command) {
        if (command == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "command é obrigatório", 400);
        }
        if (command.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        if (command.targetPlan() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }
    }

    /**
     * Normaliza texto para logs.
     *
     * @param value valor
     * @return valor normalizado
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}