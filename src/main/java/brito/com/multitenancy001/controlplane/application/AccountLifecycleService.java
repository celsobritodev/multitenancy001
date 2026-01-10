package brito.com.multitenancy001.controlplane.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountUserSummaryResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.api.mapper.AccountAdminDetailsApiMapper;
import brito.com.multitenancy001.controlplane.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.exec.PublicExecutor;
import brito.com.multitenancy001.shared.api.error.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {
	
	private final ControlPlaneUserRepository controlPlaneUserRepository;

	
	private final AccountAdminDetailsApiMapper accountAdminDetailsApiMapper;

	
	private final AccountApiMapper accountApiMapper;
	
	private final PublicExecutor publicExecutor;

	

    private final AccountRepository accountRepository;

    private final AccountOnboardingService onboardingService;
    private final AccountStatusService accountStatusService;
    private final AccountTenantUserService accountTenantUserService;

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
        return publicExecutor.run(() ->
            accountRepository.findAllByDeletedFalse()
                .stream()
                .map(accountApiMapper::toResponse)
                .toList()
        );
    }


    @Transactional(readOnly = true)
    public AccountResponse getAccountByIdWithAdmin(Long accountId) {
        return publicExecutor.run(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
            return accountApiMapper.toResponse(account);
        });
    }


   @Transactional(readOnly = true)
public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {
    return publicExecutor.run(() -> {
        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
            .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        long totalUsers = controlPlaneUserRepository.countByAccountIdAndDeletedFalse(accountId);

        return accountAdminDetailsApiMapper.toResponse(account, null, totalUsers);
    });
}



    /* =========================================================
       3. STATUS / SOFT DELETE / RESTORE
       ========================================================= */

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
        return accountStatusService.changeAccountStatus(accountId, req);
    }

    public void softDeleteAccount(Long accountId) {
        accountStatusService.softDeleteAccount(accountId);
    }

    public void restoreAccount(Long accountId) {
        accountStatusService.restoreAccount(accountId);
    }

    /* =========================================================
       4. TENANT USERS (ADMIN)
       ========================================================= */

    public List<AccountUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyActive) {
        return accountTenantUserService.listTenantUsers(accountId, onlyActive);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }
}
