package brito.com.multitenancy001.tenant.me.app;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.users.app.TenantUserService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantMeService {

    private final TenantUserService tenantUserService;
    private final TenantSchemaExecutor tenantExecutor;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;

    public TenantUser getMyProfile() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.getUser(userId, accountId)
        );
    }

    public TenantUser updateMyProfile(UpdateMyProfileRequest req) {
        if (req == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.updateProfile(
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
        if (req == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        Long userId = securityUtils.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.changeMyPassword(
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
