package brito.com.multitenancy001.controlplane.accounts.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando responsável pelas mutações de lifecycle e status de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Alterar status da conta.</li>
 *   <li>Executar soft delete.</li>
 *   <li>Executar restore.</li>
 *   <li>Coordenar auditoria e side effects no tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusCommandService {

    private final AccountStatusTransitionService accountStatusLifecycleService;
    private final AccountStatusTenantSideEffectService accountStatusTenantSideEffectService;
    private final AccountStatusAuditService accountStatusAuditService;
    private final AccountStatusSupport accountStatusSupport;
    private final AppClock appClock;

    /**
     * Altera o status da conta e aplica side effects associados.
     *
     * @param accountId id da conta
     * @param accountStatusChangeCommand comando de mudança de status
     * @return resultado consolidado da operação
     */
    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand accountStatusChangeCommand
    ) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        if (accountStatusChangeCommand == null || accountStatusChangeCommand.status() == null) {
            throw new ApiException(ApiErrorCode.STATUS_REQUIRED, "status é obrigatório", 400);
        }

        log.info("🔄 Iniciando mudança de status da conta [ID: {}] para [{}]",
                accountId,
                accountStatusChangeCommand.status());

        Instant now = appClock.instant();

        Long actorUserId = accountStatusSupport.getCurrentActorUserIdOrNull();
        String actorEmail = accountStatusSupport.getCurrentActorEmailOrNull();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "controlplane.accounts");
        details.put("event", "account_status_change");
        details.put("accountId", accountId);
        details.put("requestedStatus", accountStatusChangeCommand.status().name());
        details.put("occurredAt", now.toString());

        if (accountStatusChangeCommand.origin() != null && !accountStatusChangeCommand.origin().isBlank()) {
            details.put("origin", accountStatusChangeCommand.origin().trim());
        }

        if (accountStatusChangeCommand.reason() != null && !accountStatusChangeCommand.reason().isBlank()) {
            details.put("reason", accountStatusChangeCommand.reason().trim());
        }

        if (actorUserId != null) {
            details.put("actorUserId", actorUserId);
        }

        if (actorEmail != null) {
            details.put("actorEmail", actorEmail);
        }

        SecurityAuditActionType actionType = SecurityAuditActionType.ACCOUNT_STATUS_CHANGED;
        accountStatusAuditService.recordAudit(
                actionType,
                AuditOutcome.ATTEMPT,
                actorEmail,
                actorUserId,
                accountId,
                null,
                details
        );

        try {
            AccountStatusChangeResult result = accountStatusLifecycleService.changeAccountStatus(
                    accountId,
                    accountStatusChangeCommand,
                    details
            );

            accountStatusAuditService.recordAudit(
                    actionType,
                    AuditOutcome.SUCCESS,
                    actorEmail,
                    actorUserId,
                    accountId,
                    result.tenantSchema(),
                    details
            );

            return result;

        } catch (ApiException ex) {
            details.put("error", ex.getError());
            details.put("status", ex.getStatus());

            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                log.warn("🚫 Acesso negado ao alterar status da conta [{}]: {}", accountId, ex.getMessage());
                accountStatusAuditService.recordAudit(
                        actionType,
                        AuditOutcome.DENIED,
                        actorEmail,
                        actorUserId,
                        accountId,
                        null,
                        details
                );
            } else {
                log.error("❌ Erro ao alterar status da conta [{}]: {}", accountId, ex.getMessage());
                accountStatusAuditService.recordAudit(
                        actionType,
                        AuditOutcome.FAILURE,
                        actorEmail,
                        actorUserId,
                        accountId,
                        null,
                        details
                );
            }
            throw ex;

        } catch (Exception ex) {
            log.error("❌ Erro inesperado ao alterar status da conta [{}]", accountId, ex);
            details.put("exception", ex.getClass().getSimpleName());

            accountStatusAuditService.recordAudit(
                    actionType,
                    AuditOutcome.FAILURE,
                    actorEmail,
                    actorUserId,
                    accountId,
                    null,
                    details
            );
            throw ex;
        }
    }

    /**
     * Executa soft delete de conta e depois aplica side effect no tenant.
     *
     * @param accountId id da conta
     */
    public void softDeleteAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("🗑️ Iniciando exclusão da conta [ID: {}]", accountId);

        Account account = accountStatusLifecycleService.softDeleteAccount(accountId);
        accountStatusTenantSideEffectService.softDeleteAllTenantUsers(account);

        log.info("🎉 Exclusão da conta [{}] concluída com sucesso", accountId);
    }

    /**
     * Executa restore de conta e depois aplica side effect no tenant.
     *
     * @param accountId id da conta
     */
    public void restoreAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("🔄 Iniciando restauração da conta [ID: {}]", accountId);

        Account account = accountStatusLifecycleService.restoreAccount(accountId);
        accountStatusTenantSideEffectService.restoreAllTenantUsers(account);

        log.info("🎉 Restauração da conta [{}] concluída com sucesso", accountId);
    }
}