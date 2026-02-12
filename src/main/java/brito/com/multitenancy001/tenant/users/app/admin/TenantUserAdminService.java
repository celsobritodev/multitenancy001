package brito.com.multitenancy001.tenant.users.app.admin;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaExecutor;
import brito.com.multitenancy001.tenant.users.app.TenantUserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserService tenantUserService;
    private final SecurityUtils securityUtils;
    private final TenantSchemaExecutor tenantExecutor;

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.setSuspendedByAdmin(accountId, userId, suspended);
            return null;
        });
    }
}
