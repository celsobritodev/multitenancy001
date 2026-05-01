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
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando responsável pelas mutações de lifecycle e status de Account.
 *
 * <p>Regra V33:</p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Sem validação inline.</li>
 *   <li>Sem uso de ex.getStatus().</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusCommandService {

    private final AccountStatusTransitionService accountStatusLifecycleService;
    private final AccountStatusTenantSideEffectService accountStatusTenantSideEffectService;
    private final AccountStatusAuditService accountStatusAuditService;
    private final AccountStatusInternalFacade accountStatusInternalFacade;
    private final AppClock appClock;

    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand command
    ) {

        RequiredValidator.requireAccountId(accountId);
        RequiredValidator.requirePayload(command, ApiErrorCode.STATUS_REQUIRED, "status é obrigatório");
        RequiredValidator.requirePayload(command.status(), ApiErrorCode.STATUS_REQUIRED, "status é obrigatório");

        log.info("🔄 Iniciando mudança de status da conta [ID: {}] para [{}]",
                accountId,
                command.status());

        Instant now = appClock.instant();

        Long actorUserId = accountStatusInternalFacade.getCurrentActorUserIdOrNull();
        String actorEmail = accountStatusInternalFacade.getCurrentActorEmailOrNull();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "controlplane.accounts");
        details.put("event", "account_status_change");
        details.put("accountId", accountId);
        details.put("requestedStatus", command.status().name());
        details.put("occurredAt", now.toString());

        if (command.origin() != null && !command.origin().isBlank()) {
            details.put("origin", command.origin().trim());
        }

        if (command.reason() != null && !command.reason().isBlank()) {
            details.put("reason", command.reason().trim());
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
                    command,
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

            if (isAccessDenied(ex)) {

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

    public void softDeleteAccount(Long accountId) {

        RequiredValidator.requireAccountId(accountId);

        log.info("🗑️ Iniciando exclusão da conta [ID: {}]", accountId);

        Account account = accountStatusLifecycleService.softDeleteAccount(accountId);
        accountStatusTenantSideEffectService.softDeleteAllTenantUsers(account);

        log.info("🎉 Exclusão da conta [{}] concluída com sucesso", accountId);
    }

    public void restoreAccount(Long accountId) {

        RequiredValidator.requireAccountId(accountId);

        log.info("🔄 Iniciando restauração da conta [ID: {}]", accountId);

        Account account = accountStatusLifecycleService.restoreAccount(accountId);
        accountStatusTenantSideEffectService.restoreAllTenantUsers(account);

        log.info("🎉 Restauração da conta [{}] concluída com sucesso", accountId);
    }

    // =========================
    // HELPER
    // =========================

    private boolean isAccessDenied(ApiException ex) {
        return ApiErrorCode.ACCESS_DENIED.name().equals(ex.getError());
    }
}