package brito.com.multitenancy001.tenant.users.app.admin;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserCommandService tenantUserCommandService;
    private final SecurityUtils securityUtils;
    private final TenantExecutor tenantExecutor;

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.setSuspendedByAdmin(accountId, userId, suspended);
            return null;
        });
    }
}
