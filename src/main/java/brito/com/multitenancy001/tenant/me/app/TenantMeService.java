package brito.com.multitenancy001.tenant.me.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantMeService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;

    private final TenantExecutor tenantExecutor;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;

    public TenantUser getMyProfile() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.getUser(userId, accountId)
        );
    }

    public TenantUser updateMyProfile(UpdateMyProfileRequest req) {
        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserCommandService.updateProfile(
                        userId,
                        accountId,
                        req.name(),
                        req.phone(),
                        req.avatarUrl(),
                        req.locale(),
                        req.timezone(),
                        appClock.instant()
                )
        );
    }

    public void changeMyPassword(TenantChangeMyPasswordRequest req) {
        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long userId = securityUtils.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.changeMyPassword(
                    userId,
                    accountId,
                    req.currentPassword(),
                    req.newPassword(),
                    req.confirmNewPassword()
            );
            return null;
        });
    }
}
