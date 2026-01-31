package brito.com.multitenancy001.tenant.api.dto.users;

import brito.com.multitenancy001.infrastructure.publicschema.AccountEntitlementsSnapshot;

import java.util.List;

public record TenantUsersListResponse(
        AccountEntitlementsSnapshot entitlements, // somente para TENANT_OWNER
        List<TenantUserListItemResponse> users
) {}
