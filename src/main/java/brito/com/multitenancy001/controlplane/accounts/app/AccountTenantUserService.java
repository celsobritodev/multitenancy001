package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.integration.tenant.TenantProvisioningIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantProvisioningIntegrationService tenantProvisioningIntegrationService;

    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }

        log.info("Listing tenant users for accountId={}, onlyOperational={}", accountId, onlyOperational);

        Account account = findAccountOrThrow(accountId);
        String tenantSchema = requireTenantSchema(account);

        return tenantProvisioningIntegrationService.listUserSummaries(tenantSchema, onlyOperational);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório");
        }

        log.info("Setting suspendedByAdmin={} for userId={} in accountId={}", suspended, userId, accountId);

        Account account = findAccountOrThrow(accountId);
        String tenantSchema = requireTenantSchema(account);

        tenantProvisioningIntegrationService.setSuspendedByAdmin(tenantSchema, userId, suspended);
    }

    private Account findAccountOrThrow(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"))
        );
    }

    private String requireTenantSchema(Account account) {
        String tenantSchema = account.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_REQUIRED,
                    "Conta não possui tenantSchema associado (accountId=" + account.getId() + ")"
            );
        }
        return tenantSchema;
    }
}
