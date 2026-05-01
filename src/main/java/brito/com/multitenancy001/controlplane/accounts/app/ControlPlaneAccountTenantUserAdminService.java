package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço administrativo de usuários tenant acessados a partir do contexto
 * Control Plane e associados a uma account.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountTenantUserAdminService {

    private final AccountTenantUserService accountTenantUserService;

    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        RequiredValidator.requireAccountId(accountId);

        log.info(
                "Listando usuários do tenant associado à conta. accountId={}, onlyOperational={}",
                accountId,
                onlyOperational
        );

        return accountTenantUserService.listTenantUsers(accountId, onlyOperational);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        RequiredValidator.requireAccountId(accountId);
        RequiredValidator.requireUserId(userId);

        log.info(
                "Atualizando suspensão administrativa de usuário tenant. accountId={}, userId={}, suspended={}",
                accountId,
                userId,
                suspended
        );

        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }
}