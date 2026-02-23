package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.app.audit.TenantUserSecurityAuditRecorder;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Application Service (Tenant): Users no "current context" (identidade do request).
 *
 * Regras:
 * - Resolve accountId/userId/tenantSchema da identidade do request.
 * - Aplica entitlements (limite de usuários) antes de criar.
 * - Auditoria SOC2-like: ATTEMPT + SUCCESS/DENIED/FAILURE (append-only em public schema).
 *
 * Nota arquitetural:
 * - Commands (writes) delegam para TenantUserCommandService, que executa via TenantSchemaUnitOfWork (schema + tx).
 * - Queries continuam usando TenantExecutor quando necessário (read no schema do tenant).
 */
@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextCommandService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserQueryService tenantUserQueryService;

    private final TenantExecutor tenantExecutor;
    private final TenantRequestIdentityService requestIdentity;
    private final AccountEntitlementsGuard accountEntitlementsGuard;

    private final TenantUserSecurityAuditRecorder securityAudit;

    public void transferTenantOwner(Long toUserId) {
        /* Transfere TENANT_OWNER do usuário autenticado (from) para outro usuário (to). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long fromUserId = requestIdentity.getCurrentUserId();

        TenantUser target = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(toUserId, accountId));

        Map<String, Object> details = securityAudit.baseDetails("ownership_transfer", toUserId, target.getEmail());
        details.put("fromUserId", fromUserId);
        details.put("toUserId", toUserId);

        securityAudit.recordAttempt(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, toUserId, target.getEmail(), details);

        try {
            // ✅ command já executa schema+tx via TenantSchemaUnitOfWork
            tenantUserCommandService.transferTenantOwnerRole(accountId, tenantSchema, fromUserId, toUserId);

            securityAudit.recordSuccess(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, toUserId, target.getEmail(), details);

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, toUserId, target.getEmail(), details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, toUserId, target.getEmail(), details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, toUserId, target.getEmail(), details);
            throw ex;
        }
    }

    public TenantUser createTenantUser(TenantUserCreateRequest req) {
        /* Cria usuário no tenant (respeita limite/entitlements) + audita USER_CREATED. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);

        String name = (req.name() == null) ? null : req.name().trim();
        String email = (req.email() == null) ? null : req.email().trim().toLowerCase();

        String locale = (req.locale() == null) ? null : req.locale().trim();
        if (locale != null && locale.isBlank()) locale = null;

        String timezone = (req.timezone() == null) ? null : req.timezone().trim();
        if (timezone != null && timezone.isBlank()) timezone = null;

        LinkedHashSet<TenantPermission> perms =
                (req.permissions() == null || req.permissions().isEmpty())
                        ? null
                        : new LinkedHashSet<>(req.permissions());

        EntityOrigin origin = (req.origin() != null) ? req.origin() : EntityOrigin.ADMIN;
        if (origin == EntityOrigin.BUILT_IN) {
            throw new ApiException(ApiErrorCode.INVALID_ORIGIN, "Origin BUILT_IN não pode ser criado via API", 400);
        }

        Boolean mustChangePassword = (req.mustChangePassword() == null) ? Boolean.FALSE : req.mustChangePassword();

        long currentUsers = tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE)
        );

        accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);

        String finalLocale = locale;
        String finalTimezone = timezone;

        Map<String, Object> details = securityAudit.baseDetails("user_create", null, email);
        details.put("name", name);
        details.put("origin", origin.name());
        details.put("mustChangePassword", mustChangePassword);
        details.put("role", req.role() != null ? req.role().name() : null);
        details.put("permissionsCount", perms != null ? perms.size() : 0);

        securityAudit.recordAttempt(SecurityAuditActionType.USER_CREATED, null, email, details);

        try {
            // ✅ command já executa schema+tx via TenantSchemaUnitOfWork
            TenantUser created = tenantUserCommandService.createTenantUser(
                    accountId,
                    tenantSchema,
                    name,
                    email,
                    req.password(),
                    req.role(),
                    req.phone(),
                    req.avatarUrl(),
                    finalLocale,
                    finalTimezone,
                    perms,
                    mustChangePassword,
                    origin
            );

            details.put("createdUserId", created.getId());
            securityAudit.recordSuccess(SecurityAuditActionType.USER_CREATED, created.getId(), created.getEmail(), details);
            return created;

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(SecurityAuditActionType.USER_CREATED, null, email, details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(SecurityAuditActionType.USER_CREATED, null, email, details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(SecurityAuditActionType.USER_CREATED, null, email, details);
            throw ex;
        }
    }

    public TenantUser setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        /* Suspende/reativa usuário por admin (flag suspendedByAdmin) + audita USER_SUSPENDED/USER_RESTORED. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantUser target = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

        SecurityAuditActionType type = suspended ? SecurityAuditActionType.USER_SUSPENDED : SecurityAuditActionType.USER_RESTORED;

        Map<String, Object> details = securityAudit.baseDetails("user_suspend_by_admin", userId, target.getEmail());
        details.put("suspended", suspended);
        details.put("mode", "by_admin");

        securityAudit.recordAttempt(type, userId, target.getEmail(), details);

        try {
            // ✅ command já executa schema+tx
            tenantUserCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);

            TenantUser result = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

            securityAudit.recordSuccess(type, userId, target.getEmail(), details);
            return result;

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(type, userId, target.getEmail(), details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(type, userId, target.getEmail(), details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(type, userId, target.getEmail(), details);
            throw ex;
        }
    }

    public TenantUser setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        /* Suspende/reativa usuário por conta (flag suspendedByAccount) + audita USER_SUSPENDED/USER_RESTORED. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantUser target = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

        SecurityAuditActionType type = suspended ? SecurityAuditActionType.USER_SUSPENDED : SecurityAuditActionType.USER_RESTORED;

        Map<String, Object> details = securityAudit.baseDetails("user_suspend_by_account", userId, target.getEmail());
        details.put("suspended", suspended);
        details.put("mode", "by_account");

        securityAudit.recordAttempt(type, userId, target.getEmail(), details);

        try {
            // ✅ command já executa schema+tx
            tenantUserCommandService.setSuspendedByAccount(accountId, tenantSchema, userId, suspended);

            TenantUser result = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

            securityAudit.recordSuccess(type, userId, target.getEmail(), details);
            return result;

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(type, userId, target.getEmail(), details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(type, userId, target.getEmail(), details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(type, userId, target.getEmail(), details);
            throw ex;
        }
    }

    public void softDeleteTenantUser(Long userId) {
        /* Soft delete do usuário (deleção lógica) + audita USER_SOFT_DELETED. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantUser target = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

        Map<String, Object> details = securityAudit.baseDetails("user_soft_delete", userId, target.getEmail());
        securityAudit.recordAttempt(SecurityAuditActionType.USER_SOFT_DELETED, userId, target.getEmail(), details);

        try {
            // ✅ command já executa schema+tx
            tenantUserCommandService.softDelete(userId, accountId, tenantSchema);

            securityAudit.recordSuccess(SecurityAuditActionType.USER_SOFT_DELETED, userId, target.getEmail(), details);

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(SecurityAuditActionType.USER_SOFT_DELETED, userId, target.getEmail(), details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(SecurityAuditActionType.USER_SOFT_DELETED, userId, target.getEmail(), details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(SecurityAuditActionType.USER_SOFT_DELETED, userId, target.getEmail(), details);
            throw ex;
        }
    }

    public void hardDeleteTenantUser(Long userId) {
        /* Hard delete do usuário (deleção física). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        // ✅ assinatura nova exige tenantSchema
        tenantUserCommandService.hardDelete(userId, accountId, tenantSchema);
    }

    public TenantUser restoreTenantUser(Long userId) {
        /* Restaura usuário após soft delete + audita USER_SOFT_RESTORED. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantUser target = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

        Map<String, Object> details = securityAudit.baseDetails("user_soft_restore", userId, target.getEmail());
        securityAudit.recordAttempt(SecurityAuditActionType.USER_SOFT_RESTORED, userId, target.getEmail(), details);

        try {
            // ✅ command já executa schema+tx
            TenantUser restored = tenantUserCommandService.restore(userId, accountId, tenantSchema);

            securityAudit.recordSuccess(SecurityAuditActionType.USER_SOFT_RESTORED, userId, target.getEmail(), details);
            return restored;

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(SecurityAuditActionType.USER_SOFT_RESTORED, userId, target.getEmail(), details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(SecurityAuditActionType.USER_SOFT_RESTORED, userId, target.getEmail(), details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(SecurityAuditActionType.USER_SOFT_RESTORED, userId, target.getEmail(), details);
            throw ex;
        }
    }

    public TenantUser resetTenantUserPassword(Long userId, String newPassword) {
        /* Reset administrativo de senha (sem senha atual). */
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória", 400);

        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantUser target = tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserQueryService.getUser(userId, accountId));

        Map<String, Object> details = securityAudit.baseDetails("user_password_reset_admin", userId, target.getEmail());
        details.put("mode", "admin_reset");

        // IMPORTANTE: não logar senha.
        securityAudit.recordAttempt(SecurityAuditActionType.PASSWORD_RESET_COMPLETED, userId, target.getEmail(), details);

        try {
            // ✅ command já executa schema+tx
            TenantUser updated = tenantUserCommandService.resetPassword(userId, accountId, tenantSchema, newPassword);

            securityAudit.recordSuccess(SecurityAuditActionType.PASSWORD_RESET_COMPLETED, userId, target.getEmail(), details);
            return updated;

        } catch (ApiException ex) {
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                securityAudit.recordDenied(SecurityAuditActionType.PASSWORD_RESET_COMPLETED, userId, target.getEmail(), details);
            } else {
                details.put("error", ex.getError());
                details.put("status", ex.getStatus());
                securityAudit.recordFailure(SecurityAuditActionType.PASSWORD_RESET_COMPLETED, userId, target.getEmail(), details);
            }
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            securityAudit.recordFailure(SecurityAuditActionType.PASSWORD_RESET_COMPLETED, userId, target.getEmail(), details);
            throw ex;
        }
    }
}