package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço administrativo de usuários tenant acessados a partir do contexto
 * Control Plane e associados a uma account.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Listar usuários do tenant vinculado a uma account.</li>
 *   <li>Executar ações administrativas sobre usuários tenant em nome do
 *       Control Plane.</li>
 *   <li>Encapsular a delegação para o serviço especializado de usuários do
 *       tenant associado à conta.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Não é o serviço de comandos principal do agregado Account.</li>
 *   <li>Não executa regra de onboarding nem lifecycle de account.</li>
 *   <li>Concentra apenas a administração de tenant users via account.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountTenantUserAdminService {

    private final AccountTenantUserService accountTenantUserService;

    /**
     * Lista usuários do tenant associado à account informada.
     *
     * @param accountId id da conta
     * @param onlyOperational indica se devem ser retornados apenas usuários operacionais
     * @return lista resumida de usuários do tenant
     * @throws ApiException se o accountId for nulo
     */
    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        log.info(
                "Listando usuários do tenant associado à conta. accountId={}, onlyOperational={}",
                accountId,
                onlyOperational
        );

        return accountTenantUserService.listTenantUsers(accountId, onlyOperational);
    }

    /**
     * Suspende ou reativa um usuário do tenant por ação administrativa originada
     * no Control Plane.
     *
     * @param accountId id da conta
     * @param userId id do usuário tenant
     * @param suspended status desejado
     * @throws ApiException se accountId ou userId forem nulos
     */
    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
        }

        log.info(
                "Atualizando suspensão administrativa de usuário tenant. accountId={}, userId={}, suspended={}",
                accountId,
                userId,
                suspended
        );

        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }
}