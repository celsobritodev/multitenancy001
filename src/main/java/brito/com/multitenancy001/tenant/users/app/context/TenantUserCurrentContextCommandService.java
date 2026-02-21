package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;

/**
 * Application Service (Tenant): Users no "current context" (identidade do request).
 *
 * Regras:
 * - Resolve accountId/userId/tenantSchema da identidade do request.
 * - Aplica entitlements (limite de usuários) antes de criar.
 * - Executa dentro do schema via TenantExecutor.
 */
@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextCommandService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserQueryService tenantUserQueryService;

    private final TenantExecutor tenantExecutor;
    private final TenantRequestIdentityService requestIdentity;
    private final AccountEntitlementsGuard accountEntitlementsGuard;

    public void transferTenantOwner(Long toUserId) {
        /* Transfere TENANT_OWNER do usuário autenticado (from) para outro usuário (to). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long fromUserId = requestIdentity.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            // ✅ FIX: assinatura correta: (accountId, tenantSchema, fromUserId, toUserId)
            tenantUserCommandService.transferTenantOwnerRole(accountId, tenantSchema, fromUserId, toUserId);
            return null;
        });
    }

    public TenantUser createTenantUser(TenantUserCreateRequest req) {
        /* Cria usuário no tenant (respeita limite/entitlements). */
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

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                // ✅ FIX: assinatura correta: (accountId, tenantSchema, name, email, rawPassword, role, ...)
                tenantUserCommandService.createTenantUser(
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
                )
        );
    }

    public TenantUser setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        /* Suspende/reativa usuário por admin (flag suspendedByAdmin). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            // ✅ FIX: assinatura correta: (accountId, tenantSchema, userId, suspended)
            tenantUserCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);
            return tenantUserQueryService.getUser(userId, accountId);
        });
    }

    public TenantUser setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        /* Suspende/reativa usuário por conta (flag suspendedByAccount). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            // ✅ FIX: assinatura correta: (accountId, tenantSchema, userId, suspended)
            tenantUserCommandService.setSuspendedByAccount(accountId, tenantSchema, userId, suspended);
            return tenantUserQueryService.getUser(userId, accountId);
        });
    }

    public void softDeleteTenantUser(Long userId) {
        /* Soft delete do usuário (deleção lógica). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            // ✅ FIX: assinatura correta: (userId, accountId, tenantSchema)
            tenantUserCommandService.softDelete(userId, accountId, tenantSchema);
            return null;
        });
    }

    public void hardDeleteTenantUser(Long userId) {
        /* Hard delete do usuário (deleção física). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            // Mantém assinatura existente do seu command service (não estava no erro atual).
            tenantUserCommandService.hardDelete(userId, accountId);
            return null;
        });
    }

    public TenantUser restoreTenantUser(Long userId) {
        /* Restaura usuário após soft delete. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                // ✅ FIX: assinatura correta: (userId, accountId, tenantSchema)
                tenantUserCommandService.restore(userId, accountId, tenantSchema)
        );
    }

    public TenantUser resetTenantUserPassword(Long userId, String newPassword) {
        /* Reset administrativo de senha (sem senha atual). */
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória", 400);

        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                // ✅ FIX: assinatura correta: (userId, accountId, tenantSchema, newPassword)
                tenantUserCommandService.resetPassword(userId, accountId, tenantSchema, newPassword)
        );
    }
}