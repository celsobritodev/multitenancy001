package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.signup.app.AccountOnboardingService;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando do agregado Account no Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Delegar criação/onboarding de account.</li>
 *   <li>Delegar mudança de status e lifecycle.</li>
 *   <li>Delegar operações administrativas sobre usuários do tenant.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Este serviço concentra operações de escrita e comando.</li>
 *   <li>Não deve conter lógica de consulta paginada nem filtros administrativos de leitura.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountCommandService {

    private final AccountOnboardingService accountOnboardingService;
    private final AccountStatusService accountStatusService;
    private final AccountTenantUserService accountTenantUserService;

    /**
     * Orquestra criação de account via onboarding.
     *
     * @param signupCommand comando de signup
     * @return resultado do onboarding
     */
    public SignupResult createAccount(SignupCommand signupCommand) {
        if (signupCommand == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "signupCommand é obrigatório", 400);
        }

        log.info("Iniciando createAccount via onboarding.");
        return accountOnboardingService.createAccount(signupCommand);
    }

    /**
     * Altera status da conta.
     *
     * @param accountId id da conta
     * @param accountStatusChangeCommand comando de status
     * @return resultado consolidado
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

        return accountStatusService.changeAccountStatus(accountId, accountStatusChangeCommand);
    }

    /**
     * Executa soft delete de account.
     *
     * @param accountId id da conta
     */
    public void softDeleteAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        accountStatusService.softDeleteAccount(accountId);
    }

    /**
     * Restaura account deletada logicamente.
     *
     * @param accountId id da conta
     */
    public void restoreAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        accountStatusService.restoreAccount(accountId);
    }

    /**
     * Lista usuários do tenant associado à account.
     *
     * @param accountId id da conta
     * @param onlyOperational indica se deve listar apenas operacionais
     * @return lista de usuários resumidos
     */
    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        return accountTenantUserService.listTenantUsers(accountId, onlyOperational);
    }

    /**
     * Suspende ou reativa usuário do tenant por ação administrativa.
     *
     * @param accountId id da conta
     * @param userId id do usuário
     * @param suspended status desejado
     */
    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
        }

        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }
}