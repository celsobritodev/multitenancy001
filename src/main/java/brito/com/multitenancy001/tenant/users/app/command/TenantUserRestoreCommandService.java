package brito.com.multitenancy001.tenant.users.app.command;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de restore de usuários do tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserRestoreCommandService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;
    private final LoginIdentityService loginIdentityService;
    private final AfterTransactionCompletion afterTransactionCompletion;
    private final TenantUserAuditService tenantUserAuditService;
    private final TenantUserActorResolver tenantUserActorResolver;

    public TenantUser restore(Long userId, Long accountId, String tenantSchema) {
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
        AtomicReference<String> restoredEmail = new AtomicReference<>();

        TenantUser saved = tenantSchemaUnitOfWork.tx(normalizedTenantSchema, () -> {
            TenantUserAuditService.Actor actor = tenantUserActorResolver.resolveActorOrNull(accountId, normalizedTenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

            TenantUser restoredUser = tenantUserAuditService.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_RESTORED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    normalizedTenantSchema,
                    tenantUserAuditService.m("scope", "TENANT", "reason", "softRestore"),
                    null,
                    () -> {
                        user.restore();
                        return tenantUserRepository.save(user);
                    }
            );

            restoredEmail.set(restoredUser.getEmail());
            return restoredUser;
        });

        if (StringUtils.hasText(restoredEmail.get()) && accountId != null) {
            afterTransactionCompletion.runAfterCompletion(() -> {
                try {
                    loginIdentityService.ensureTenantIdentity(restoredEmail.get(), accountId);
                    log.info("ensureTenantIdentity executado após restore. email={} accountId={}",
                            restoredEmail.get(), accountId);
                } catch (Exception e) {
                    log.error("Falha ao garantir identidade após restore (best-effort). email={} accountId={}",
                            restoredEmail.get(), accountId, e);
                }
            });
        }

        return saved;
    }
}