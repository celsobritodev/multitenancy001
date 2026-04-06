package brito.com.multitenancy001.tenant.users.app.command;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.subscription.app.TenantUsageSnapshotAfterCommitService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de soft delete de usuários do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar contexto de mutação.</li>
 *   <li>Executar guardas de domínio antes da exclusão lógica.</li>
 *   <li>Persistir o soft delete no schema tenant.</li>
 *   <li>Executar side effects pós-transação de identidade e refresh de usage snapshot.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserSoftDeleteCommandService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;
    private final AppClock appClock;
    private final LoginIdentityService loginIdentityService;
    private final AfterTransactionCompletion afterTransactionCompletion;
    private final TenantUserAuditService tenantUserAuditService;
    private final TenantUserActorResolver tenantUserActorResolver;
    private final TenantUserMutationGuard tenantUserMutationGuard;
    private final TenantUsageSnapshotAfterCommitService tenantUsageSnapshotAfterCommitService;

    /**
     * Executa soft delete do usuário no tenant informado.
     *
     * @param userId id do usuário
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void softDelete(Long userId, Long accountId, String tenantSchema) {
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
        AtomicReference<String> deletedEmail = new AtomicReference<>();

        tenantSchemaUnitOfWork.tx(normalizedTenantSchema, () -> {
            TenantUserAuditService.Actor actor =
                    tenantUserActorResolver.resolveActorOrNull(accountId, normalizedTenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

            if (user.isDeleted()) {
                log.info(
                        "Soft delete ignorado porque usuário já está deletado. accountId={}, tenantSchema={}, userId={}",
                        accountId,
                        normalizedTenantSchema,
                        userId
                );
                return null;
            }

            tenantUserMutationGuard.requireNotBuiltInForMutation(
                    user,
                    "Nao e permitido excluir usuario BUILT_IN"
            );

            if (tenantUserMutationGuard.isActiveOwner(user)) {
                tenantUserMutationGuard.requireWillStillHaveAtLeastOneActiveOwner(
                        accountId,
                        "Nao e permitido excluir o ultimo TENANT_OWNER ativo"
                );
            }

            deletedEmail.set(user.getEmail());

            tenantUserAuditService.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_DELETED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    normalizedTenantSchema,
                    tenantUserAuditService.m(
                            "scope", "TENANT",
                            "reason", "softDelete"
                    ),
                    null,
                    () -> {
                        Instant now = appClock.instant();
                        user.softDelete(now, appClock.epochMillis());
                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });

        if (StringUtils.hasText(deletedEmail.get()) && accountId != null) {
            afterTransactionCompletion.runAfterCompletion(() -> {
                try {
                    loginIdentityService.deleteTenantIdentity(deletedEmail.get(), accountId);
                    log.info(
                            "deleteTenantIdentity executado após softDelete. email={} accountId={}",
                            deletedEmail.get(),
                            accountId
                    );
                } catch (Exception e) {
                    log.error(
                            "Falha ao remover identidade após softDelete (best-effort). email={} accountId={}",
                            deletedEmail.get(),
                            accountId,
                            e
                    );
                }
            });
        }

        tenantUsageSnapshotAfterCommitService.scheduleRefreshAfterCommit(
                accountId,
                normalizedTenantSchema
        );

        log.info(
                "Soft delete de usuário concluído. accountId={}, tenantSchema={}, userId={}, deletedEmail={}",
                accountId,
                normalizedTenantSchema,
                userId,
                deletedEmail.get()
        );
    }
}