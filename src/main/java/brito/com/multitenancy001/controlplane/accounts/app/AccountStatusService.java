package brito.com.multitenancy001.controlplane.accounts.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.integration.tenant.TenantUsersIntegrationService;
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
    private final TenantUsersIntegrationService tenantUsersIntegrationService;

    private final AppClock appClock;

    public AccountStatusChangeResult changeAccountStatus(Long accountId, AccountStatusChangeCommand cmd) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        if (cmd == null || cmd.status() == null) throw new ApiException("STATUS_REQUIRED", "status é obrigatório", 400);

        return publicUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);
            AccountStatus previous = account.getStatus();

            AccountStatus newStatus = cmd.status();
            account.setStatus(newStatus);

            // Se reativou, garante que não está deletado
            if (newStatus == AccountStatus.ACTIVE && account.isDeleted()) {
                account.restore();
            }

            accountRepository.save(account);

            // Fronteira explícita: schemaName (dado da conta) -> tenantSchema (contexto de execução)
            String tenantSchema = account.getSchemaName();

            int affected = 0;
            boolean applied = false;
            AccountStatusSideEffect action = AccountStatusSideEffect.NONE;

            if (newStatus == AccountStatus.SUSPENDED) {
                // ✅ Este método agora é SAFE (suspende todos menos TENANT_OWNER)
                affected = tenantUsersIntegrationService.suspendAllUsersByAccount(tenantSchema, account.getId());
                applied = true;
                action = AccountStatusSideEffect.SUSPEND_BY_ACCOUNT;
            } else if (newStatus == AccountStatus.ACTIVE) {
                affected = tenantUsersIntegrationService.unsuspendAllUsersByAccount(tenantSchema, account.getId());
                applied = true;
                action = AccountStatusSideEffect.UNSUSPEND_BY_ACCOUNT;
            } else if (newStatus == AccountStatus.CANCELLED) {
                affected = cancelAccount(account);
                applied = true;
                action = AccountStatusSideEffect.CANCELLED;
            }

            return new AccountStatusChangeResult(
                    account.getId(),
                    account.getStatus().name(),
                    previous == null ? null : previous.name(),
                    appClock.instant(),
                    account.getSchemaName(), // CP continua falando schemaName no resultado
                    applied,
                    action.name(),
                    affected
            );
        });
    }

    public void softDeleteAccount(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);

        publicUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount()) {
                throw new ApiException("BUILTIN_ACCOUNT_PROTECTED", "Não é permitido excluir contas do sistema", 403);
            }

            account.softDelete(appClock.instant());
            accountRepository.save(account);

            String tenantSchema = account.getSchemaName();
            tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId());
        });
    }

    public void restoreAccount(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);

        publicUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount() && account.isDeleted()) {
                throw new ApiException("BUILTIN_ACCOUNT_PROTECTED", "Contas do sistema não podem ser restauradas via este endpoint", 403);
            }

            account.restore();
            accountRepository.save(account);

            String tenantSchema = account.getSchemaName();
            tenantUsersIntegrationService.restoreAllUsersByAccount(tenantSchema, account.getId());
        });
    }

    private int cancelAccount(Account account) {
        publicUnitOfWork.requiresNew(() -> {
            if (!account.isDeleted()) {
                account.softDelete(appClock.instant());
            }
            account.setStatus(AccountStatus.CANCELLED);
            accountRepository.save(account);
        });

        String tenantSchema = account.getSchemaName();
        return tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId());
    }

    private Account getAccountByIdRaw(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    private enum AccountStatusSideEffect {
        NONE,
        SUSPEND_BY_ACCOUNT,
        UNSUSPEND_BY_ACCOUNT,
        CANCELLED
    }
}
