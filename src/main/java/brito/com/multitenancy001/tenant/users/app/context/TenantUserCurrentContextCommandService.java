package brito.com.multitenancy001.tenant.users.app.context;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextCommandService {

    private final SecurityUtils securityUtils;
    private final TenantExecutor tenantExecutor;
    private final TenantUserCommandService tenantUserCommandService;

    public TenantUser updateMyProfile(String name, String phone, String avatarUrl, String timezone) {
        Long accountId = securityUtils.getCurrentAccountId();
        Long userId = securityUtils.getCurrentUserId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.updateMyProfile(userId, accountId, name, phone, avatarUrl, timezone)
        );
    }

    public TenantUser changeMyPassword(String currentPassword, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória");
        }

        Long accountId = securityUtils.getCurrentAccountId();
        Long userId = securityUtils.getCurrentUserId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.changeMyPassword(userId, accountId, currentPassword, newPassword)
        );
    }

    public TenantUser softDeleteMyUser() {
        Long accountId = securityUtils.getCurrentAccountId();
        Long userId = securityUtils.getCurrentUserId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.softDelete(userId, accountId)
        );
    }

    public TenantUser restoreMyUser() {
        Long accountId = securityUtils.getCurrentAccountId();
        Long userId = securityUtils.getCurrentUserId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.restore(userId, accountId)
        );
    }

    public TenantUser resetTenantUserPassword(Long userId, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória");
        }

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.resetPassword(userId, accountId, newPassword)
        );
    }
}
