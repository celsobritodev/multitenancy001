package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaExecutor;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUsersListView;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextQueryService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantSchemaExecutor tenantExecutor;
    private final SecurityUtils securityUtils;
    private final AccountEntitlementsService accountEntitlementsService;

    public TenantUsersListView listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        TenantRole currentRole = securityUtils.getCurrentTenantRole();
        boolean isOwner = currentRole != null && currentRole.isTenantOwner();

        AccountEntitlementsSnapshot entitlements = null;
        if (isOwner) {
            entitlements = accountEntitlementsService.resolveEffectiveByAccountId(accountId);
        }

        AccountEntitlementsSnapshot finalEntitlements = entitlements;

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            List<TenantUser> users = tenantUserQueryService.listUsers(accountId);
            return new TenantUsersListView(isOwner, finalEntitlements, users);
        });
    }

    public List<TenantUser> listEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.listEnabledUsers(accountId)
        );
    }

    public TenantUser getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.getUser(userId, accountId)
        );
    }

    public TenantUser getEnabledTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.getEnabledUser(userId, accountId)
        );
    }

    public long countEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.countEnabledUsersByAccount(accountId)
        );
    }
}
