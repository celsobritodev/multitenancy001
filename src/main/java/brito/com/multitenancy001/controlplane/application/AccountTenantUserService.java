package brito.com.multitenancy001.controlplane.application;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountUserSummaryResponse;
import brito.com.multitenancy001.controlplane.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.exec.PublicExecutor;
import brito.com.multitenancy001.infrastructure.exec.TenantExecutor;
import brito.com.multitenancy001.infrastructure.exec.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {
	
	private final TenantUserApiMapper tenantUserApiMapper;


    private final PublicExecutor publicExecutor;
    private final TenantExecutor tenantExecutor;
    private final TxExecutor txExecutor;

    private final AccountRepository accountRepository;
    private final TenantUserRepository tenantUserRepository;

    public List<AccountUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyActive) {

        Account account = publicExecutor.run(() ->
            accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        return tenantExecutor.runOrThrow(account.getSchemaName(), "users_tenant", () ->
            txExecutor.tenantReadOnlyTx(() -> {
                List<TenantUser> users = onlyActive
                    ? tenantUserRepository.findActiveUsersByAccount(account.getId())
                    : tenantUserRepository.findByAccountId(account.getId());

                return users.stream().map(tenantUserApiMapper::toAccountUserSummary).toList();
            })
        );
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {

        Account account = publicExecutor.run(() ->
            accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        tenantExecutor.runOrThrow(account.getSchemaName(), "users_tenant", () ->
            txExecutor.tenantTx(() -> {
                int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
                if (updated == 0) {
                    throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
                }
                return null;
            })
        );
    }
}
