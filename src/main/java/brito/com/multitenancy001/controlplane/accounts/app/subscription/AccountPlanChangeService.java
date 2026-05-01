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

    public PlanEligibilityResult previewChange(ChangeAccountPlanCommand command) {
        validateCommand(command);

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(command.accountId())
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta não encontrada com id: " + command.accountId()
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

    public AccountPlanChangeResult applyEligibleDowngrade(ChangeAccountPlanCommand command) {
        validateCommand(command);

        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(command.accountId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada com id: " + command.accountId()
                    ));

            PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
            PlanEligibilityResult eligibility = planChangePolicy.requireEligibleChange(usage, command.targetPlan());

            if (eligibility.changeType() != PlanChangeType.DOWNGRADE) {
                throw new ApiException(
                        ApiErrorCode.INVALID_REQUEST,
                        "Operação não é downgrade elegível"
                );
            }

            return applyPlanChange(account, command, eligibility);
        });
    }

    public AccountPlanChangeResult applyApprovedUpgrade(ChangeAccountPlanCommand command) {
        validateCommand(command);

        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(command.accountId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada com id: " + command.accountId()
                    ));

            PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
            PlanEligibilityResult eligibility = planChangePolicy.requireEligibleChange(usage, command.targetPlan());

            if (eligibility.changeType() != PlanChangeType.UPGRADE) {
                throw new ApiException(
                        ApiErrorCode.INVALID_REQUEST,
                        "Operação não é upgrade aprovado"
                );
            }

            return applyPlanChange(account, command, eligibility);
        });
    }

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
     * 🔥 VALIDAÇÃO CENTRALIZADA (V33)
     */
    private void validateCommand(ChangeAccountPlanCommand command) {
        SubscriptionValidator.requireCommand(command);
        SubscriptionValidator.requireAccountId(command.accountId());
        SubscriptionValidator.requireTargetPlan(command.targetPlan());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}