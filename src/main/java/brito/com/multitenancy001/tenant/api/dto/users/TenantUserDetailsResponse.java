package brito.com.multitenancy001.tenant.api.dto.users;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;

public record TenantUserDetailsResponse(
        Long id,
        String username,
        String name,
        String email,
        String role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long accountId,
        LinkedHashSet<String> permissions
) {
	public TenantUserDetailsResponse {
	    if (permissions == null) permissions = new LinkedHashSet<>();
	}


    public static TenantUserDetailsResponse from(TenantUser tenantUser) {
        boolean enabled = !tenantUser.isDeleted() && !tenantUser.isSuspendedByAccount() && !tenantUser.isSuspendedByAdmin();
        return new TenantUserDetailsResponse(
                tenantUser.getId(),
                tenantUser.getUsername(),
                tenantUser.getName(),
                tenantUser.getEmail(),
                tenantUser.getRole() != null ? tenantUser.getRole().name() : null,
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled,
                tenantUser.getCreatedAt(),
                tenantUser.getUpdatedAt(),
                tenantUser.getAccountId(),
                tenantUser.getPermissions()
        );
    }
}
