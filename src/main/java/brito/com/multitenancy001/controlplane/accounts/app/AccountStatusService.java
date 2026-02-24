package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusSideEffect;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.integration.tenant.TenantUsersIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Application Service (Control Plane): mudança de status e lifecycle da Account.
 *
 * Responsabilidades:
 * - Alterar Account.status no public schema.
 * - Aplicar side-effects no Tenant (suspender/reativar/soft-delete users) via Integration Service.
 * - Registrar auditoria append-only via SecurityAuditService para mudanças sensíveis de estado.
 *
 * Regras:
 * - Controller não acessa repository diretamente.
 * - Fonte de tempo: AppClock.
 * - Auditoria: ATTEMPT/SUCCESS/DENIED/FAILURE, details JSON estruturado, sem segredos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantUsersIntegrationService tenantUsersIntegrationService;
    private final AppClock appClock;

    private final SecurityAuditService securityAuditService;
    private final ControlPlaneRequestIdentityService requestIdentity;
    private final JsonDetailsMapper jsonDetailsMapper;

    public AccountStatusChangeResult changeAccountStatus(Long accountId, AccountStatusChangeCommand cmd) {
        /* Altera status da account e aplica side-effects no tenant + auditoria append-only. */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (cmd == null || cmd.status() == null) throw new ApiException(ApiErrorCode.STATUS_REQUIRED, "status é obrigatório", 400);

        log.info("🔄 Iniciando mudança de status da conta [ID: {}] para [{}]", accountId, cmd.status());

        Instant now = appClock.instant();

        Long actorUserId = nullSafe(() -> requestIdentity.getCurrentUserId());
        String actorEmail = nullSafe(() -> requestIdentity.getCurrentEmail());

        // DETAILS base (estruturado)
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "controlplane.accounts");
        details.put("event", "account_status_change");
        details.put("accountId", accountId);
        details.put("requestedStatus", cmd.status().name());
        details.put("occurredAt", now.toString());

        if (cmd.origin() != null && !cmd.origin().isBlank()) details.put("origin", cmd.origin().trim());
        if (cmd.reason() != null && !cmd.reason().isBlank()) details.put("reason", cmd.reason().trim());

        if (actorUserId != null) details.put("actorUserId", actorUserId);
        if (actorEmail != null) details.put("actorEmail", actorEmail);

        // Action type existente no seu enum (não quebra histórico)
        SecurityAuditActionType actionType = SecurityAuditActionType.ACCOUNT_STATUS_CHANGED;

        // ATTEMPT
        recordAudit(actionType, AuditOutcome.ATTEMPT, actorEmail, actorUserId, accountId, null, details);

        try {
            AccountStatusChangeResult result = publicSchemaUnitOfWork.tx(() -> {

                Account account = getAccountByIdRaw(accountId);
                AccountStatus previous = account.getStatus();

                log.debug("Status atual da conta: [{}] -> novo status: [{}]", previous, cmd.status());

                AccountStatus newStatus = cmd.status();
                account.setStatus(newStatus);

                // Reativar também restaura soft-delete se necessário
                if (newStatus == AccountStatus.ACTIVE && account.isDeleted()) {
                    account.restore();
                    details.put("restoredSoftDelete", true);
                    log.debug("Conta estava deletada e foi restaurada");
                }

                accountRepository.save(account);

                // ✅ Fronteira explícita: tenantSchema (CP) -> execução Tenant
                String tenantSchema = account.getTenantSchema();
                details.put("tenantSchema", tenantSchema);
                details.put("fromStatus", previous != null ? previous.name() : null);
                details.put("toStatus", newStatus.name());

                int affected = 0;
                boolean applied = false;
                AccountStatusSideEffect action = AccountStatusSideEffect.NONE;

                if (newStatus == AccountStatus.SUSPENDED) {
                    log.info("⏸️ Aplicando suspensão aos usuários do tenant [{}]", tenantSchema);
                    affected = tenantUsersIntegrationService.suspendAllUsersByAccount(tenantSchema, account.getId());
                    applied = true;
                    action = AccountStatusSideEffect.SUSPEND_BY_ACCOUNT;

                } else if (newStatus == AccountStatus.ACTIVE) {
                    log.info("▶️ Reativando usuários do tenant [{}]", tenantSchema);
                    affected = tenantUsersIntegrationService.unsuspendAllUsersByAccount(tenantSchema, account.getId());
                    applied = true;
                    action = AccountStatusSideEffect.UNSUSPEND_BY_ACCOUNT;

                } else if (newStatus == AccountStatus.CANCELLED) {
                    log.info("❌ Cancelando conta e removendo usuários do tenant [{}]", tenantSchema);
                    affected = cancelAccount(account);
                    applied = true;
                    action = AccountStatusSideEffect.CANCEL_ACCOUNT;
                }

                details.put("sideEffectApplied", applied);
                details.put("sideEffectAction", action.name());
                details.put("affectedUsers", affected);

                log.info("✅ Status da conta [{}] alterado com sucesso. Ação: {}, Usuários afetados: {}", 
                        accountId, action, affected);

                return new AccountStatusChangeResult(
                        account.getId(),
                        account.getStatus(),
                        previous,
                        appClock.instant(),
                        tenantSchema,
                        applied,
                        action,
                        affected
                );
            });

            // SUCCESS
            recordAudit(actionType, AuditOutcome.SUCCESS, actorEmail, actorUserId, accountId, null, details);
            return result;

        } catch (ApiException ex) {
            details.put("error", ex.getError());
            details.put("status", ex.getStatus());

            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                log.warn("🚫 Acesso negado ao alterar status da conta [{}]: {}", accountId, ex.getMessage());
                recordAudit(actionType, AuditOutcome.DENIED, actorEmail, actorUserId, accountId, null, details);
            } else {
                log.error("❌ Erro ao alterar status da conta [{}]: {}", accountId, ex.getMessage());
                recordAudit(actionType, AuditOutcome.FAILURE, actorEmail, actorUserId, accountId, null, details);
            }
            throw ex;

        } catch (Exception ex) {
            log.error("❌ Erro inesperado ao alterar status da conta [{}]", accountId, ex);
            details.put("exception", ex.getClass().getSimpleName());
            recordAudit(actionType, AuditOutcome.FAILURE, actorEmail, actorUserId, accountId, null, details);
            throw ex;
        }
    }

  public void softDeleteAccount(Long accountId) {
    if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

    log.info("🗑️ Iniciando exclusão da conta [ID: {}]", accountId);

    // PRIMEIRA TRANSAÇÃO: Marca a Account como deletada no Public Schema
    Account account = publicSchemaUnitOfWork.tx(() -> {
        log.debug("Passo 1/2: Marcando conta como deletada no schema public");
        Account acc = getAccountByIdRaw(accountId);

        if (acc.isBuiltInAccount()) {
            log.warn("🚫 Tentativa de excluir conta do sistema [ID: {}]", accountId);
            throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_PROTECTED, 
                "Contas do sistema não podem ser excluídas", 403);
        }

        acc.softDelete(appClock.instant());
        Account saved = accountRepository.save(acc);
        log.info("✅ Conta [{} - {}] marcada como deletada", accountId, acc.getDisplayName());
        return saved;
    });

    // SEGUNDA TRANSAÇÃO: Executa em UM NOVO THREAD para garantir isolamento completo
    String tenantSchema = account.getTenantSchema();
    log.info("📦 Passo 2/2: Removendo usuários do tenant [{}]", tenantSchema);

    try {
        // Executa em um novo contexto transacional completamente isolado
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            return publicSchemaUnitOfWork.requiresNew(() -> {
                log.debug("Executando limpeza de usuários em transação isolada");
                return tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId());
            });
        });

        Integer usuariosRemovidos = future.get(30, TimeUnit.SECONDS); // timeout de 30s

        if (usuariosRemovidos != null && usuariosRemovidos > 0) {
            log.info("✅ {} usuário(s) do tenant [{}] foram removidos", usuariosRemovidos, tenantSchema);
        } else {
            log.info("ℹ️ Nenhum usuário encontrado no tenant [{}] para remover", tenantSchema);
        }

        log.info("🎉 Exclusão da conta [{}] concluída com sucesso", accountId);

    } catch (Exception e) {
        log.warn("⚠️ A conta [{}] foi excluída, mas houve um problema ao remover os usuários do tenant [{}].", 
                accountId, tenantSchema);
        log.warn("   Motivo: Não foi possível completar a operação no tenant. A limpeza dos usuários precisará ser feita manualmente.");
        log.debug("Detalhes técnicos:", e);
    }
}

    public void restoreAccount(Long accountId) {
        /* Restaura account (se permitido) + restaura users do tenant (side-effect). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

        log.info("🔄 Iniciando restauração da conta [ID: {}]", accountId);

        // PRIMEIRA TRANSAÇÃO: Restaura a Account no Public Schema
        Account account = publicSchemaUnitOfWork.tx(() -> {
            log.debug("Passo 1/2: Restaurando conta no schema public");
            Account acc = getAccountByIdRaw(accountId);

            if (acc.isBuiltInAccount() && acc.isDeleted()) {
                log.warn("🚫 Tentativa de restaurar conta do sistema [ID: {}]", accountId);
                throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_PROTECTED, 
                    "Contas do sistema não podem ser restauradas", 403);
            }

            acc.restore();
            Account saved = accountRepository.save(acc);
            log.info("✅ Conta [{} - {}] restaurada", accountId, acc.getDisplayName());
            return saved;
        });

        // SEGUNDA TRANSAÇÃO (REQUIRES_NEW): Restaura os usuários do tenant
        String tenantSchema = account.getTenantSchema();
        log.info("📦 Restaurando usuários do tenant [{}]", tenantSchema);

        try {
            publicSchemaUnitOfWork.requiresNew(() -> {
                log.debug("Executando restauração de usuários em transação separada");
                tenantUsersIntegrationService.restoreAllUsersByAccount(tenantSchema, account.getId());
                return null;
            });

            log.info("✅ Usuários do tenant [{}] restaurados com sucesso", tenantSchema);
            log.info("🎉 Restauração da conta [{}] concluída com sucesso", accountId);

        } catch (Exception e) {
            log.warn("⚠️ A conta [{}] foi restaurada, mas houve um problema ao restaurar os usuários do tenant [{}].", 
                    accountId, tenantSchema);
            log.warn("   Motivo: {}", e.getMessage());
            log.debug("Detalhes técnicos:", e);
            // Não relançar a exceção - a operação principal já foi concluída
        }
    }

    private int cancelAccount(Account account) {
        /* Cancela account: soft delete (se necessário) + status CANCELLED + soft delete users. */
        log.info("📝 Cancelando conta [ID: {} - {}]", account.getId(), account.getDisplayName());

        // PRIMEIRA TRANSAÇÃO: Atualiza a Account
        publicSchemaUnitOfWork.requiresNew(() -> {
            if (!account.isDeleted()) {
                account.softDelete(appClock.instant());
            }
            account.setStatus(AccountStatus.CANCELLED);
            accountRepository.save(account);
            log.info("✅ Conta [{}] marcada como CANCELADA", account.getId());
            return null;
        });

        // SEGUNDA TRANSAÇÃO: Deleta os usuários do tenant
        String tenantSchema = account.getTenantSchema();
        try {
            int removidos = publicSchemaUnitOfWork.requiresNew(() -> 
                tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId())
            );
            log.info("✅ {} usuário(s) do tenant removidos durante cancelamento", removidos);
            return removidos;
        } catch (Exception e) {
            log.warn("⚠️ Cancelamento parcial: usuários do tenant não foram removidos. Motivo: {}", e.getMessage());
            log.debug("Detalhes:", e);
            return 0;
        }
    }

    private Account getAccountByIdRaw(Long accountId) {
        /* Busca account por id sem exigir enabled/ready (raw). */
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));
    }

    private void recordAudit(SecurityAuditActionType actionType,
                             AuditOutcome outcome,
                             String actorEmail,
                             Long actorUserId,
                             Long accountId,
                             String tenantSchema,
                             Map<String, Object> details) {
        /* Persiste evento append-only (public schema) com JSON details estruturado. */
        String detailsJson = (details == null) ? null : jsonDetailsMapper.toJsonNode(details).toString();

        securityAuditService.record(
                actionType,
                outcome,
                actorEmail,
                actorUserId,
                null,
                null,
                accountId,
                tenantSchema,
                detailsJson
        );
    }

    private static <T> T nullSafe(SupplierEx<T> fn) {
        /* Para jobs sem auth: captura exceções de identidade e devolve null. */
        try {
            return fn.get();
        } catch (Exception ex) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SupplierEx<T> {
        T get();
    }
}