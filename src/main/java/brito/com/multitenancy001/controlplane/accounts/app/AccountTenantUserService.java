package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

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

    /**
     * Padronização:
     * - APP retorna o "contract" (UserSummaryData)
     * - API decide o formato HTTP (AccountTenantUserSummaryResponse) via mapper
     * - enabled é calculado no AccountUserApiMapper (um único lugar)
     */
    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {

        Account account = publicUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        String tenantSchema = account.getSchemaName();

        return tenantUserProvisioningFacade
                .listUserSummaries(tenantSchema, account.getId(), onlyOperational);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {

        Account account = publicUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        String tenantSchema = account.getSchemaName();

        tenantUserProvisioningFacade.setSuspendedByAdmin(tenantSchema, account.getId(), userId, suspended);
    }
}
