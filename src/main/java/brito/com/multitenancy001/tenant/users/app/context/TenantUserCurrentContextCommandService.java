package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
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

@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextCommandService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserQueryService tenantUserQueryService;

    private final TenantExecutor tenantExecutor;
    private final SecurityUtils securityUtils;
    private final AccountEntitlementsGuard accountEntitlementsGuard;

    public void transferTenantOwner(Long toUserId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long fromUserId = securityUtils.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.transferTenantOwnerRole(accountId, fromUserId, toUserId);
            return null;
        });
    }

    public TenantUser createTenantUser(TenantUserCreateRequest req) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);

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
            throw new ApiException("INVALID_ORIGIN", "Origin BUILT_IN não pode ser criado via API", 400);
        }

        Boolean mustChangePassword = (req.mustChangePassword() == null) ? Boolean.FALSE : req.mustChangePassword();

        long currentUsers = tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE)
        );

        accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);

        String finalLocale = locale;
        String finalTimezone = timezone;

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.createTenantUser(
                        accountId,
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
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.setSuspendedByAdmin(accountId, userId, suspended);
            return tenantUserQueryService.getUser(userId, accountId);
        });
    }

    public TenantUser setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.setSuspendedByAccount(accountId, userId, suspended);
            return tenantUserQueryService.getUser(userId, accountId);
        });
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.softDelete(userId, accountId);
            return null;
        });
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.hardDelete(userId, accountId);
            return null;
        });
    }

    public TenantUser restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.restore(userId, accountId)
        );
    }

    public TenantUser resetTenantUserPassword(Long userId, String newPassword) {
        if (!StringUtils.hasText(newPassword)) throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.resetPassword(userId, accountId, newPassword)
        );
    }
}
