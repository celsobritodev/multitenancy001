package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountTenantUserSummaryData;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserProvisioningFacade;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantUserProvisioningFacade tenantUserProvisioningFacade;

    public List<AccountTenantUserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {

        Account account = publicUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        List<UserSummaryData> data = tenantUserProvisioningFacade
                .listUserSummaries(account.getSchemaName(), account.getId(), onlyOperational);

        return data.stream()
                .map(AccountTenantUserService::toSummary)
                .toList();
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {

        Account account = publicUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        tenantUserProvisioningFacade.setSuspendedByAdmin(account.getSchemaName(), account.getId(), userId, suspended);
    }

    private static AccountTenantUserSummaryData toSummary(UserSummaryData user) {
        boolean enabled =
                !user.deleted()
                && !user.suspendedByAccount()
                && !user.suspendedByAdmin();

        return new AccountTenantUserSummaryData(
                user.id(),
                user.accountId(),
                user.name(),
                user.email(),
                user.role(),
                user.suspendedByAccount(),
                user.suspendedByAdmin(),
                enabled
        );
    }
}

