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

                AccountStatus newStatus = cmd.status();
                account.setStatus(newStatus);

                // Reativar também restaura soft-delete se necessário
                if (newStatus == AccountStatus.ACTIVE && account.isDeleted()) {
                    account.restore();
                    details.put("restoredSoftDelete", true);
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
                    action = AccountStatusSideEffect.CANCEL_ACCOUNT;
                }

                details.put("sideEffectApplied", applied);
                details.put("sideEffectAction", action.name());
                details.put("affectedUsers", affected);

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
                recordAudit(actionType, AuditOutcome.DENIED, actorEmail, actorUserId, accountId, null, details);
            } else {
                recordAudit(actionType, AuditOutcome.FAILURE, actorEmail, actorUserId, accountId, null, details);
            }
            throw ex;

        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            recordAudit(actionType, AuditOutcome.FAILURE, actorEmail, actorUserId, accountId, null, details);
            throw ex;
        }
    }

    public void softDeleteAccount(Long accountId) {
        /* Soft delete de account + soft delete de users do tenant (side-effect). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

        publicSchemaUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount()) {
                throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_PROTECTED, "Não é permitido excluir contas do sistema", 403);
            }

            account.softDelete(appClock.instant());
            accountRepository.save(account);

            String tenantSchema = account.getTenantSchema();
            tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId());

            return null;
        });
    }

    public void restoreAccount(Long accountId) {
        /* Restaura account (se permitido) + restaura users do tenant (side-effect). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

        publicSchemaUnitOfWork.tx(() -> {

            Account account = getAccountByIdRaw(accountId);

            if (account.isBuiltInAccount() && account.isDeleted()) {
                throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_PROTECTED, "Contas do sistema não podem ser restauradas via este endpoint", 403);
            }

            account.restore();
            accountRepository.save(account);

            String tenantSchema = account.getTenantSchema();
            tenantUsersIntegrationService.restoreAllUsersByAccount(tenantSchema, account.getId());

            return null;
        });
    }

    private int cancelAccount(Account account) {
        /* Cancela account: soft delete (se necessário) + status CANCELLED + soft delete users. */
        publicSchemaUnitOfWork.requiresNew(() -> {
            if (!account.isDeleted()) {
                account.softDelete(appClock.instant());
            }
            account.setStatus(AccountStatus.CANCELLED);
            accountRepository.save(account);
        });

        String tenantSchema = account.getTenantSchema();
        return tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId());
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