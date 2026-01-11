package brito.com.multitenancy001.tenant.application.user.admin;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.application.user.TenantUserTxService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserTxService tenantUserTxService;
    private final SecurityUtils securityUtils;

    private void runInTenant(String schema, Runnable action) {
        TenantContext.bind(schema);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        runInTenant(schema, () ->
                tenantUserTxService.setSuspendedByAdmin(userId, accountId, suspended)
        );
    }
}
