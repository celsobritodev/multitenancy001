package brito.com.multitenancy001.controlplane.accounts.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserProvisioningFacade;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantUserProvisioningFacade tenantUserProvisioningFacade;
    private final AppClock appClock;

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        }
        if (req == null || req.status() == null) {
            throw new ApiException("STATUS_REQUIRED", "status é obrigatório", 400);
        }

        return publicUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);
            AccountStatus previous = account.getStatus();

            AccountStatus newStatus = req.status();
            account.setStatus(newStatus);

            // Mantém seu comportamento atual:
            // - quando volta para ACTIVE, limpa deletedAt
            // OBS: se isso não for desejado, remova este bloco e use somente restoreAccount().
            if (newStatus == AccountStatus.ACTIVE) {
                account.setDeletedAt(null);
            }

            accountRepository.save(account);

            int affected = 0;
            boolean applied = false;
            AccountStatusSideEffect action = AccountStatusSideEffect.NONE;

            if (newStatus == AccountStatus.SUSPENDED) {
                affected = tenantUserProvisioningFacade.suspendAllUsersByAccount(account.getSchemaName(), account.getId());
                applied = true;
                action = AccountStatusSideEffect.SUSPEND_BY_ACCOUNT;
            } else if (newStatus == AccountStatus.ACTIVE) {
                affected = tenantUserProvisioningFacade.unsuspendAllUsersByAccount(account.getSchemaName(), account.getId());
                applied = true;
                action = AccountStatusSideEffect.UNSUSPEND_BY_ACCOUNT;
            } else if (newStatus == AccountStatus.CANCELLED) {
                affected = cancelAccount(account);
                applied = true;
                action = AccountStatusSideEffect.CANCELLED;
            }

            return buildStatusChangeResponse(account, previous, applied, action, affected);
        });
    }

    public void softDeleteAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        }

        publicUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount()) {
                throw new ApiException(
                        "BUILTIN_ACCOUNT_PROTECTED",
                        "Não é permitido excluir contas do sistema",
                        403
                );
            }

            account.softDelete(appClock.now());
            accountRepository.save(account);

            tenantUserProvisioningFacade.softDeleteAllUsersByAccount(account.getSchemaName(), account.getId());
        });
    }

    public void restoreAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        }

        publicUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount() && account.isDeleted()) {
                throw new ApiException(
                        "BUILTIN_ACCOUNT_PROTECTED",
                        "Contas do sistema não podem ser restauradas via este endpoint",
                        403
                );
            }

            account.restore();
            accountRepository.save(account);

            tenantUserProvisioningFacade.restoreAllUsersByAccount(account.getSchemaName(), account.getId());
        });
    }

    private int cancelAccount(Account account) {
        // Mantém sua ideia de "marca deleted_at" em TX separada.
        publicUnitOfWork.requiresNew(() -> {
            account.setDeletedAt(appClock.now());
            accountRepository.save(account);
        });

        return tenantUserProvisioningFacade.softDeleteAllUsersByAccount(account.getSchemaName(), account.getId());
    }

    private Account getAccountByIdRaw(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    private AccountStatusChangeResponse buildStatusChangeResponse(
            Account account,
            AccountStatus previous,
            boolean tenantUsersUpdated,
            AccountStatusSideEffect action,
            int count
    ) {
        return new AccountStatusChangeResponse(
                account.getId(),
                account.getStatus().name(),
                previous == null ? null : previous.name(),
                appClock.now(),
                account.getSchemaName(),
                new AccountStatusChangeResponse.SideEffects(
                        tenantUsersUpdated,
                        action.name(),
                        count
                )
        );
    }

    /**
     * Side effect tipado para manter padrão de consistência.
     * Ainda é serializado como String no response (action.name()).
     */
    private enum AccountStatusSideEffect {
        NONE,
        SUSPEND_BY_ACCOUNT,
        UNSUSPEND_BY_ACCOUNT,
        CANCELLED
    }
}
