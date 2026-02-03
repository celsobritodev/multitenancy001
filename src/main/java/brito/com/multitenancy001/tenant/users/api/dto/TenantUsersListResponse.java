package brito.com.multitenancy001.tenant.users.api.dto;

import java.util.List;

import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;

public record TenantUsersListResponse(
        AccountEntitlementsSnapshot entitlements, // somente para TENANT_OWNER
        List<TenantUserListItemResponse> users
) {}
