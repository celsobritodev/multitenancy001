package brito.com.multitenancy001.tenant.users.app.query;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.app.TenantUserService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantUserQueryService {

    private final TenantUserService tenantUserService;
    private final TenantExecutor tenantExecutor;
    private final SecurityUtils securityUtils;
    private final AccountEntitlementsService accountEntitlementsService;

    public TenantUsersListView listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        TenantRole currentRole = securityUtils.getCurrentTenantRole();
        boolean isOwner = currentRole != null && currentRole.isTenantOwner();

        AccountEntitlementsSnapshot entitlements = null;
        if (isOwner) {
            entitlements = accountEntitlementsService.resolveEffectiveByAccountId(accountId);
        }

        AccountEntitlementsSnapshot finalEntitlements = entitlements;

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            List<TenantUser> users = tenantUserService.listUsers(accountId);
            return new TenantUsersListView(isOwner, finalEntitlements, users);
        });
    }

    public List<TenantUser> listEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.listEnabledUsers(accountId)
        );
    }

    public TenantUser getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.getUser(userId, accountId)
        );
    }

    public TenantUser getEnabledTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.getEnabledUser(userId, accountId)
        );
    }

    public long countEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.countEnabledUsersByAccount(accountId)
        );
    }
}
