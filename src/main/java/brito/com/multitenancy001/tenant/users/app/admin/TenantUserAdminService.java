package brito.com.multitenancy001.tenant.users.app.admin;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.tenant.users.app.TenantUserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserService tenantUserService;
    private final SecurityUtils securityUtils;
    private final TenantExecutor tenantExecutor;

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.setSuspendedByAdmin(accountId, userId, suspended);
            return null;
        });
    }
}
