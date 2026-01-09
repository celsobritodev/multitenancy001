package brito.com.multitenancy001.controlplane.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infra.exec.PublicExecutor;
import brito.com.multitenancy001.infra.exec.TenantExecutor;
import brito.com.multitenancy001.infra.exec.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final PublicExecutor publicExec;
    private final TenantExecutor tenantExec;
    private final TxExecutor tx;

    private final AccountRepository accountRepository;
    private final TenantUserRepository tenantUserRepository;

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
        return tx.publicTx(() -> publicExec.run(() -> {

            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            AccountStatus previous = account.getStatus(); // ✅ captura antes de alterar

            // ... mantenha aqui suas regras de transição, validações, etc ...

            account.setStatus(req.status());
            if (req.status() == AccountStatus.ACTIVE) {
                account.setDeletedAt(null);
            }
            accountRepository.save(account);

            int affected = 0;
            boolean applied = false;
            String action = "NONE";

            if (req.status() == AccountStatus.SUSPENDED) {
                affected = suspendTenantUsersByAccount(account);
                applied = true;
                action = "SUSPEND_BY_ACCOUNT";
            } else if (req.status() == AccountStatus.ACTIVE) {
                affected = unsuspendTenantUsersByAccount(account);
                applied = true;
                action = "UNSUSPEND_BY_ACCOUNT";
            } else if (req.status() == AccountStatus.CANCELLED) {
                affected = cancelAccount(account);
                applied = true;
                action = "CANCELLED";
            }

            return buildStatusChangeResponse(account, previous, applied, action, affected);
        }));
    }

    private int cancelAccount(Account account) {
        // 1) PUBLIC (REQUIRES_NEW)
        tx.publicRequiresNew(() -> publicExec.run(() -> {
            account.setDeletedAt(LocalDateTime.now());
            accountRepository.save(account);
            return null;
        }));

        // 2) TENANT (REQUIRES_NEW), best-effort (retorna 0 se schema/tabela não existir)
        return tenantExec.runIfReady(
            account.getSchemaName(),
            "users_tenant",
            () -> tx.tenantRequiresNew(() -> {
                List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
                users.forEach(TenantUser::softDelete);
                tenantUserRepository.saveAll(users);
                return users.size();
            }),
            0
        );
    }

    protected int suspendTenantUsersByAccount(Account account) {
        return tenantExec.runIfReady(
            account.getSchemaName(),
            "users_tenant",
            () -> tx.tenantRequiresNew(() -> tenantUserRepository.suspendAllByAccount(account.getId())),
            0
        );
    }

    protected int unsuspendTenantUsersByAccount(Account account) {
        return tenantExec.runIfReady(
            account.getSchemaName(),
            "users_tenant",
            () -> tx.tenantRequiresNew(() -> tenantUserRepository.unsuspendAllByAccount(account.getId())),
            0
        );
    }

    public void softDeleteAccount(Long accountId) {
        tx.publicTx(() -> publicExec.run(() -> {

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
        tx.publicTx(() -> publicExec.run(() -> {

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

    private void softDeleteTenantUsers(Long accountId) {
        Account account = tx.publicReadOnlyTx(() -> publicExec.run(() -> getAccountByIdRaw(accountId)));

        tenantExec.runIfReady(account.getSchemaName(), "users_tenant", () -> {
            tx.tenantRequiresNew(() -> {
                List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
                users.forEach(u -> { if (!u.isDeleted()) u.softDelete(); });
                tenantUserRepository.saveAll(users);
                return null;
            });
            return null;
        }, null);
    }

    private void restoreTenantUsers(Long accountId) {
        Account account = tx.publicReadOnlyTx(() -> publicExec.run(() -> getAccountByIdRaw(accountId)));

        tenantExec.runIfReady(account.getSchemaName(), "users_tenant", () -> {
            tx.tenantRequiresNew(() -> {
                List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
                users.forEach(u -> { if (u.isDeleted()) u.restore(); });
                tenantUserRepository.saveAll(users);
                return null;
            });
            return null;
        }, null);
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
