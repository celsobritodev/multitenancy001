package brito.com.multitenancy001.controlplane.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final AccountRepository accountRepository;

    private final AccountOnboardingService onboardingService;
    private final AccountStatusService statusService;
    private final AccountTenantUserService tenantUserService;

    /* =========================================================
       1. ONBOARDING / SIGNUP
       ========================================================= */

    public AccountResponse createAccount(SignupRequest request) {
        return onboardingService.createAccount(request);
    }

    /* =========================================================
       2. CONSULTAS (PUBLIC)
       ========================================================= */

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccounts() {
        TenantSchemaContext.clearTenantSchema();
        return accountRepository.findAllByDeletedFalse()
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByIdWithAdmin(Long accountId) {
        TenantSchemaContext.clearTenantSchema();
        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
        return AccountResponse.fromEntity(account);
    }

    @Transactional(readOnly = true)
    public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {
        TenantSchemaContext.clearTenantSchema();
        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        // (Se você ainda não tem lookup do admin do tenant, mantém null)
        return AccountAdminDetailsResponse.from(account, null);
    }

    /* =========================================================
       3. STATUS / SOFT DELETE / RESTORE
       ========================================================= */

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
        return statusService.changeAccountStatus(accountId, req);
    }

    public void softDeleteAccount(Long accountId) {
        statusService.softDeleteAccount(accountId);
    }

    public void restoreAccount(Long accountId) {
        statusService.restoreAccount(accountId);
    }

    /* =========================================================
       4. TENANT USERS (ADMIN)
       ========================================================= */

    public List<TenantUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyActive) {
        return tenantUserService.listTenantUsers(accountId, onlyActive);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        tenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }
}
