package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusSideEffect;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelas transições de estado de Account no public schema.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Alterar status persistido da conta.</li>
 *   <li>Executar soft delete e restore no public schema.</li>
 *   <li>Delegar side effects externos para serviço específico.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusLifecycleService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountStatusTenantSideEffectService accountStatusTenantSideEffectService;
    private final AppClock appClock;

    /**
     * Altera o status da conta no public schema e executa side effects apropriados.
     *
     * @param accountId id da conta
     * @param accountStatusChangeCommand comando de mudança de status
     * @param details mapa de detalhes para enriquecimento de auditoria
     * @return resultado consolidado da operação
     */
    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand accountStatusChangeCommand,
            Map<String, Object> details
    ) {
        return publicSchemaUnitOfWork.tx(() -> {
            Account account = getAccountByIdRaw(accountId);
            AccountStatus previousStatus = account.getStatus();
            AccountStatus newStatus = accountStatusChangeCommand.status();

            log.debug("Status atual da conta: [{}] -> novo status: [{}]", previousStatus, newStatus);

            account.setStatus(newStatus);

            if (newStatus == AccountStatus.ACTIVE && account.isDeleted()) {
                account.restore();
                details.put("restoredSoftDelete", true);
                log.debug("Conta estava deletada e foi restaurada");
            }

            accountRepository.save(account);

            String tenantSchema = account.getTenantSchema();
            details.put("tenantSchema", tenantSchema);
            details.put("fromStatus", previousStatus != null ? previousStatus.name() : null);
            details.put("toStatus", newStatus.name());

            int affectedUsers = 0;
            boolean sideEffectApplied = false;
            AccountStatusSideEffect sideEffectAction = AccountStatusSideEffect.NONE;

            if (newStatus == AccountStatus.SUSPENDED) {
                log.info("⏸️ Aplicando suspensão aos usuários do tenant [{}]", tenantSchema);
                affectedUsers = accountStatusTenantSideEffectService.suspendAllTenantUsers(account);
                sideEffectApplied = true;
                sideEffectAction = AccountStatusSideEffect.SUSPEND_BY_ACCOUNT;

            } else if (newStatus == AccountStatus.ACTIVE) {
                log.info("▶️ Reativando usuários do tenant [{}]", tenantSchema);
                affectedUsers = accountStatusTenantSideEffectService.unsuspendAllTenantUsers(account);
                sideEffectApplied = true;
                sideEffectAction = AccountStatusSideEffect.UNSUSPEND_BY_ACCOUNT;

            } else if (newStatus == AccountStatus.CANCELLED) {
                log.info("❌ Cancelando conta e removendo usuários do tenant [{}]", tenantSchema);
                affectedUsers = cancelAccount(account);
                sideEffectApplied = true;
                sideEffectAction = AccountStatusSideEffect.CANCEL_ACCOUNT;
            }

            details.put("sideEffectApplied", sideEffectApplied);
            details.put("sideEffectAction", sideEffectAction.name());
            details.put("affectedUsers", affectedUsers);

            log.info("✅ Status da conta [{}] alterado com sucesso. Ação: {}, Usuários afetados: {}",
                    accountId,
                    sideEffectAction,
                    affectedUsers);

            return new AccountStatusChangeResult(
                    account.getId(),
                    account.getStatus(),
                    previousStatus,
                    appClock.instant(),
                    tenantSchema,
                    sideEffectApplied,
                    sideEffectAction,
                    affectedUsers
            );
        });
    }

    /**
     * Marca a conta como deletada logicamente no public schema.
     *
     * @param accountId id da conta
     * @return conta persistida
     */
    public Account softDeleteAccount(Long accountId) {
        return publicSchemaUnitOfWork.tx(() -> {
            log.debug("Passo 1/2: Marcando conta como deletada no schema public");

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount()) {
                log.warn("🚫 Tentativa de excluir conta do sistema [ID: {}]", accountId);
                throw new ApiException(
                        ApiErrorCode.BUILTIN_ACCOUNT_PROTECTED,
                        "Contas do sistema não podem ser excluídas",
                        403
                );
            }

            account.softDelete(appClock.instant());
            Account saved = accountRepository.save(account);

            log.info("✅ Conta [{} - {}] marcada como deletada", accountId, account.getDisplayName());
            return saved;
        });
    }

    /**
     * Restaura conta deletada logicamente no public schema.
     *
     * @param accountId id da conta
     * @return conta persistida
     */
    public Account restoreAccount(Long accountId) {
        return publicSchemaUnitOfWork.tx(() -> {
            log.debug("Passo 1/2: Restaurando conta no schema public");

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount() && account.isDeleted()) {
                log.warn("🚫 Tentativa de restaurar conta do sistema [ID: {}]", accountId);
                throw new ApiException(
                        ApiErrorCode.BUILTIN_ACCOUNT_PROTECTED,
                        "Contas do sistema não podem ser restauradas",
                        403
                );
            }

            account.restore();
            Account saved = accountRepository.save(account);

            log.info("✅ Conta [{} - {}] restaurada", accountId, account.getDisplayName());
            return saved;
        });
    }

    /**
     * Executa cancelamento lógico da conta no public schema e remove usuários do tenant.
     *
     * @param account conta alvo
     * @return quantidade de usuários afetados no tenant
     */
    private int cancelAccount(Account account) {
        log.info("📝 Cancelando conta [ID: {} - {}]", account.getId(), account.getDisplayName());

        publicSchemaUnitOfWork.requiresNew(() -> {
            if (!account.isDeleted()) {
                account.softDelete(appClock.instant());
            }

            account.setStatus(AccountStatus.CANCELLED);
            accountRepository.save(account);

            log.info("✅ Conta [{}] marcada como CANCELADA", account.getId());
            return null;
        });

        return accountStatusTenantSideEffectService.softDeleteAllTenantUsersForCancellation(account);
    }

    /**
     * Busca account por id sem exigir enabled/ready.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    private Account getAccountByIdRaw(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada",
                        404
                ));
    }
}