package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.provisioning.app.TenantUserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantUserProvisioningService tenantUserProvisioningFacade;

    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        String tenantSchema = account.getTenantSchema();

        return tenantUserProvisioningFacade
                .listUserSummaries(tenantSchema, account.getId(), onlyOperational);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );

        String tenantSchema = account.getTenantSchema();

        tenantUserProvisioningFacade.setSuspendedByAdmin(tenantSchema, account.getId(), userId, suspended);
    }
}
