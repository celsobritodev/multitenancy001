package brito.com.multitenancy001.controlplane.application;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.api.dto.users.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.controlplane.api.mapper.AccountUserApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserAdminBridge;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {

    private final PublicExecutor publicExecutor;
    private final AccountRepository accountRepository;
    private final TenantUserAdminBridge tenantUserAdminBridge;
    private final AccountUserApiMapper accountUserApiMapper;

    public List<AccountTenantUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyOperational) {
        Account account = publicExecutor.run(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        List<UserSummaryData> data = tenantUserAdminBridge
                .listUserSummaries(account.getSchemaName(), account.getId(), onlyOperational);

        return data.stream()
                .map(accountUserApiMapper::toAccountUserSummary)
                .toList();
    }


    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {

        Account account = publicExecutor.run(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        tenantUserAdminBridge.setSuspendedByAdmin(account.getSchemaName(), account.getId(), userId, suspended);
    }
}
