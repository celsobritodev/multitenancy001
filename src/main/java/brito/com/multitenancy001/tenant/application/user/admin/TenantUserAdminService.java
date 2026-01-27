package brito.com.multitenancy001.tenant.application.user.admin;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.tenant.application.user.TenantUserTxService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserTxService tenantUserTxService;
    private final SecurityUtils securityUtils;
    private final TenantExecutor tenantExecutor;

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        tenantExecutor.run(schema, () -> {
            tenantUserTxService.setSuspendedByAdmin(accountId, userId, suspended);
            return null;
        });
    }
}
