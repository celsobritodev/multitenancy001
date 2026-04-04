package brito.com.multitenancy001.controlplane.accounts.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.signup.app.AccountOnboardingService;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comandos do agregado Account no contexto Control Plane.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Delegar criação de account via fluxo de onboarding/signup.</li>
 *   <li>Delegar operações de lifecycle da conta, como mudança de status,
 *       soft delete e restore.</li>
 *   <li>Centralizar comandos próprios do agregado Account.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Este serviço não executa consultas administrativas de leitura.</li>
 *   <li>Este serviço não deve concentrar administração de usuários tenant.</li>
 *   <li>Este serviço atua como entry point de escrita do agregado Account.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountCommandService {

    private final AccountOnboardingService accountOnboardingService;
    private final AccountStatusFacade accountStatusService;

    /**
     * Orquestra a criação de uma nova account via fluxo de signup/onboarding.
     *
     * @param signupCommand comando de signup
     * @return resultado consolidado do onboarding
     * @throws ApiException se o comando for nulo
     */
    public SignupResult createAccount(SignupCommand signupCommand) {
        if (signupCommand == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "signupCommand é obrigatório", 400);
        }

        log.info("Iniciando createAccount via onboarding.");
        return accountOnboardingService.createAccount(signupCommand);
    }

    /**
     * Altera o status de uma conta existente.
     *
     * @param accountId id da conta
     * @param accountStatusChangeCommand comando de mudança de status
     * @return resultado consolidado da mudança de status
     * @throws ApiException se o accountId ou o comando forem inválidos
     */
    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand accountStatusChangeCommand
    ) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        if (accountStatusChangeCommand == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "cmd é obrigatório", 400);
        }

        log.info("Alterando status da conta. accountId={}", accountId);
        return accountStatusService.changeAccountStatus(accountId, accountStatusChangeCommand);
    }

    /**
     * Executa soft delete de uma conta.
     *
     * @param accountId id da conta
     * @throws ApiException se o accountId for nulo
     */
    public void softDeleteAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        log.info("Executando softDeleteAccount. accountId={}", accountId);
        accountStatusService.softDeleteAccount(accountId);
    }

    /**
     * Restaura uma conta deletada logicamente.
     *
     * @param accountId id da conta
     * @throws ApiException se o accountId for nulo
     */
    public void restoreAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        log.info("Executando restoreAccount. accountId={}", accountId);
        accountStatusService.restoreAccount(accountId);
    }
}