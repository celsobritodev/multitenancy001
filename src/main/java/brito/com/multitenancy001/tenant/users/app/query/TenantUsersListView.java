package brito.com.multitenancy001.tenant.users.app.query;

import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;

import java.util.List;

public record TenantUsersListView(
        boolean isOwner,
        AccountEntitlementsSnapshot entitlements,
        List<TenantUser> users
) {}
