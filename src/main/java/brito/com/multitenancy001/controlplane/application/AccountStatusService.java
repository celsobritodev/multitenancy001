package brito.com.multitenancy001.controlplane.application;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserProvisioningFacade;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final PublicUnitOfWork publicUow;
    private final AccountRepository accountRepository;
    private final TenantUserProvisioningFacade tenantUserAdminBridge;
    private final AppClock appClock;

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
        return publicUow.tx(() -> {

            Account account = getAccountByIdRaw(accountId);
            AccountStatus previous = account.getStatus();

            account.setStatus(req.status());
            if (req.status() == AccountStatus.ACTIVE) {
                account.setDeletedAt(null);
            }
            accountRepository.save(account);

            int affected = 0;
            boolean applied = false;
            String action = "NONE";

            if (req.status() == AccountStatus.SUSPENDED) {
                affected = tenantUserAdminBridge.suspendAllUsersByAccount(account.getSchemaName(), account.getId());
                applied = true;
                action = "SUSPEND_BY_ACCOUNT";
            } else if (req.status() == AccountStatus.ACTIVE) {
                affected = tenantUserAdminBridge.unsuspendAllUsersByAccount(account.getSchemaName(), account.getId());
                applied = true;
                action = "UNSUSPEND_BY_ACCOUNT";
            } else if (req.status() == AccountStatus.CANCELLED) {
                affected = cancelAccount(account);
                applied = true;
                action = "CANCELLED";
            }

            return buildStatusChangeResponse(account, previous, applied, action, affected);
        });
    }

    public void softDeleteAccount(Long accountId) {
        publicUow.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount()) {
                throw new ApiException("BUILTIN_ACCOUNT_PROTECTED",
                        "Não é permitido excluir contas do sistema", 403);
            }

            account.softDelete(appClock.now());
            accountRepository.save(account);

            tenantUserAdminBridge.softDeleteAllUsersByAccount(account.getSchemaName(), account.getId());
        });
    }

    public void restoreAccount(Long accountId) {
        publicUow.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount() && account.isDeleted()) {
                throw new ApiException("BUILTIN_ACCOUNT_PROTECTED",
                        "Contas do sistema não podem ser restauradas via este endpoint", 403);
            }

            account.restore();
            accountRepository.save(account);

            tenantUserAdminBridge.restoreAllUsersByAccount(account.getSchemaName(), account.getId());
        });
    }

    private int cancelAccount(Account account) {
        // Se você quer garantir "marca deleted_at" em TX separada, mantém requiresNew.
        // Se não precisa, pode trocar por publicUow.tx(...) direto.
        publicUow.requiresNew(() -> {
            account.setDeletedAt(appClock.now());
            accountRepository.save(account);
        });

        return tenantUserAdminBridge.softDeleteAllUsersByAccount(account.getSchemaName(), account.getId());
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
                appClock.now(),
                account.getSchemaName(),
                new AccountStatusChangeResponse.SideEffects(tenantUsersUpdated, action, count)
        );
    }
}
