package brito.com.multitenancy001.tenant.users.app.command;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.app.TenantUsageSnapshotAfterCommitService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de suspensão/reativação de usuários do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Aplicar suspensão por admin ou por conta.</li>
 *   <li>Executar guardas de mutação de usuário sensível.</li>
 *   <li>Executar side effects pós-transação para identidade e refresh de usage snapshot.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserSuspensionCommandService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;
    private final LoginIdentityService loginIdentityService;
    private final AfterTransactionCompletion afterTransactionCompletion;
    private final TenantUserAuditService tenantUserAuditService;
    private final TenantUserActorResolver tenantUserActorResolver;
    private final TenantUserMutationGuard tenantUserMutationGuard;
    private final TenantUsageSnapshotAfterCommitService tenantUsageSnapshotAfterCommitService;

    /**
     * Suspende ou reativa usuário por ação administrativa.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     * @param userId id do usuário
     * @param suspended status alvo
     */
    public void setSuspendedByAdmin(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, true);
    }

    /**
     * Suspende ou reativa usuário por ação da própria conta.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     * @param userId id do usuário
     * @param suspended status alvo
     */
    public void setSuspendedByAccount(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, false);
    }

    /**
     * Aplica suspensão ou reativação.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     * @param userId id do usuário
     * @param suspended status alvo
     * @param byAdmin true se for suspensão administrativa
     */
    private void setSuspension(Long accountId, String tenantSchema, Long userId, boolean suspended, boolean byAdmin) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);
        }

        final String normalizedTenantSchema = tenantSchema.trim();

        TenantUser userBefore = tenantSchemaUnitOfWork.readOnly(normalizedTenantSchema, () ->
                tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404))
        );

        final String userEmail = userBefore.getEmail();
        final boolean wasSuspended = byAdmin
                ? userBefore.isSuspendedByAdmin()
                : userBefore.isSuspendedByAccount();

        tenantSchemaUnitOfWork.tx(normalizedTenantSchema, () -> {
            TenantUserAuditService.Actor actor =
                    tenantUserActorResolver.resolveActorOrNull(accountId, normalizedTenantSchema);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

            tenantUserMutationGuard.requireNotBuiltInForMutation(
                    user,
                    "Não é permitido suspender usuario BUILT_IN"
            );

            if (suspended && tenantUserMutationGuard.isActiveOwner(user)) {
                tenantUserMutationGuard.requireWillStillHaveAtLeastOneActiveOwner(
                        accountId,
                        "Nao e permitido suspender o ultimo TENANT_OWNER ativo"
                );
            }

            SecurityAuditActionType action = suspended
                    ? SecurityAuditActionType.USER_SUSPENDED
                    : SecurityAuditActionType.USER_RESTORED;

            String reason = byAdmin ? "suspendedByAdmin" : "suspendedByAccount";

            tenantUserAuditService.auditAttemptSuccessFail(
                    action,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    normalizedTenantSchema,
                    tenantUserAuditService.m(
                            "scope", "TENANT",
                            "reason", reason,
                            "suspended", suspended
                    ),
                    null,
                    () -> {
                        int updated = byAdmin
                                ? tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended)
                                : tenantUserRepository.setSuspendedByAccount(accountId, userId, suspended);

                        if (updated == 0) {
                            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404);
                        }
                        return null;
                    }
            );

            return null;
        });

        if (!suspended && wasSuspended) {
            TenantUser userAfter = tenantSchemaUnitOfWork.readOnly(normalizedTenantSchema, () ->
                    tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId).orElse(null)
            );

            if (userAfter != null && userAfter.isEnabledDomain()) {
                afterTransactionCompletion.runAfterCompletion(() -> {
                    try {
                        loginIdentityService.ensureTenantIdentity(userEmail, accountId);
                        log.info(
                                "ensureTenantIdentity executado após reativação. email={} accountId={} byAdmin={}",
                                userEmail,
                                accountId,
                                byAdmin
                        );
                    } catch (Exception e) {
                        log.error(
                                "Falha ao garantir identidade após reativação (best-effort). email={} accountId={}",
                                userEmail,
                                accountId,
                                e
                        );
                    }
                });
            }
        }

        tenantUsageSnapshotAfterCommitService.scheduleRefreshAfterCommit(
                accountId,
                normalizedTenantSchema
        );

        log.info(
                "Suspensão/reativação de usuário concluída. accountId={}, tenantSchema={}, userId={}, suspended={}, byAdmin={}, userEmail={}",
                accountId,
                normalizedTenantSchema,
                userId,
                suspended,
                byAdmin,
                userEmail
        );
    }
}