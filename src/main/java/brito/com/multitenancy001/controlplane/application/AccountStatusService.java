package brito.com.multitenancy001.controlplane.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.exec.PublicExecutor;
import brito.com.multitenancy001.infrastructure.exec.TxExecutor;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserAdminBridge;
import brito.com.multitenancy001.shared.api.error.ApiException;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final PublicExecutor publicExecutor;
    private final TxExecutor txExecutor;
    private final AccountRepository accountRepository;
    private final TenantUserAdminBridge tenantUserAdminBridge;


    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest accountStatusChangeRequest) {
        return txExecutor.publicTx(() -> publicExecutor.run(() -> {

            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            AccountStatus previous = account.getStatus(); // ✅ captura antes de alterar

            // ... mantenha aqui suas regras de transição, validações, etc ...

            account.setStatus(accountStatusChangeRequest.status());
            if (accountStatusChangeRequest.status() == AccountStatus.ACTIVE) {
                account.setDeletedAt(null);
            }
            accountRepository.save(account);

            int affected = 0;
            boolean applied = false;
            String action = "NONE";

            if (accountStatusChangeRequest.status() == AccountStatus.SUSPENDED) {
                affected = suspendTenantUsersByAccount(account);
                applied = true;
                action = "SUSPEND_BY_ACCOUNT";
            } else if (accountStatusChangeRequest.status() == AccountStatus.ACTIVE) {
                affected = unsuspendTenantUsersByAccount(account);
                applied = true;
                action = "UNSUSPEND_BY_ACCOUNT";
            } else if (accountStatusChangeRequest.status() == AccountStatus.CANCELLED) {
                affected = cancelAccount(account);
                applied = true;
                action = "CANCELLED";
            }

            return buildStatusChangeResponse(account, previous, applied, action, affected);
        }));
    }

   protected int suspendTenantUsersByAccount(Account account) {
    return tenantUserAdminBridge.suspendAllUsersByAccount(account.getSchemaName(), account.getId());
}

protected int unsuspendTenantUsersByAccount(Account account) {
    return tenantUserAdminBridge.unsuspendAllUsersByAccount(account.getSchemaName(), account.getId());
}

private int cancelAccount(Account account) {
    txExecutor.publicRequiresNew(() -> publicExecutor.run(() -> {
        account.setDeletedAt(LocalDateTime.now());
        accountRepository.save(account);
        return null;
    }));

    // soft-delete users best-effort
    return tenantUserAdminBridge.softDeleteAllUsersByAccount(account.getSchemaName(), account.getId());
}

private void softDeleteTenantUsers(Long accountId) {
    Account account = txExecutor.publicReadOnlyTx(() ->
        publicExecutor.run(() -> getAccountByIdRaw(accountId))
    );

    tenantUserAdminBridge.softDeleteAllUsersByAccount(account.getSchemaName(), account.getId());
}

private void restoreTenantUsers(Long accountId) {
    Account account = txExecutor.publicReadOnlyTx(() ->
        publicExecutor.run(() -> getAccountByIdRaw(accountId))
    );

    tenantUserAdminBridge.restoreAllUsersByAccount(account.getSchemaName(), account.getId());
}



    public void softDeleteAccount(Long accountId) {
        txExecutor.publicTx(() -> publicExecutor.run(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isSystemAccount()) {
                throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
                        "Não é permitido excluir contas do sistema", 403);
            }

            account.softDelete();
            accountRepository.save(account);

            softDeleteTenantUsers(accountId);

            return null;
        }));
    }

    public void restoreAccount(Long accountId) {
        txExecutor.publicTx(() -> publicExecutor.run(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isSystemAccount() && account.isDeleted()) {
                throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
                        "Contas do sistema não podem ser restauradas via este endpoint", 403);
            }

            account.restore();
            accountRepository.save(account);

            restoreTenantUsers(accountId);

            return null;
        }));
    }

    private Account getAccountByIdRaw(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

  
    private AccountStatusChangeResponse buildStatusChangeResponse(
            Account account,
            AccountStatus previous,
            boolean tenantUsersUpdated,
            String action,
            int count
    ) {
        return new AccountStatusChangeResponse(
                account.getId(),
                account.getStatus().name(),
                previous.name(),
                LocalDateTime.now(),
                account.getSchemaName(),
                new AccountStatusChangeResponse.SideEffects(tenantUsersUpdated, action, count)
        );
    }
}
