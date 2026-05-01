package brito.com.multitenancy001.controlplane.accounts.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.signup.app.AccountOnboardingService;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comandos do agregado Account no contexto Control Plane.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountCommandService {

    private final AccountOnboardingService accountOnboardingService;
    private final AccountStatusFacade accountStatusService;

    public SignupResult createAccount(SignupCommand signupCommand) {
        RequiredValidator.requirePayload(
                signupCommand,
                ApiErrorCode.INVALID_REQUEST,
                "signupCommand é obrigatório"
        );

        log.info("Iniciando createAccount via onboarding.");
        return accountOnboardingService.createAccount(signupCommand);
    }

    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand accountStatusChangeCommand
    ) {
        RequiredValidator.requireAccountId(accountId);
        RequiredValidator.requirePayload(
                accountStatusChangeCommand,
                ApiErrorCode.INVALID_REQUEST,
                "cmd é obrigatório"
        );

        log.info("Alterando status da conta. accountId={}", accountId);
        return accountStatusService.changeAccountStatus(accountId, accountStatusChangeCommand);
    }

    public void softDeleteAccount(Long accountId) {
        RequiredValidator.requireAccountId(accountId);

        log.info("Executando softDeleteAccount. accountId={}", accountId);
        accountStatusService.softDeleteAccount(accountId);
    }

    public void restoreAccount(Long accountId) {
        RequiredValidator.requireAccountId(accountId);

        log.info("Executando restoreAccount. accountId={}", accountId);
        accountStatusService.restoreAccount(accountId);
    }
}