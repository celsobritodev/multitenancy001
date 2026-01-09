package brito.com.multitenancy001.tenant.api.dto.users;

import brito.com.multitenancy001.tenant.domain.user.TenantUser;

public record TenantUserSummaryResponse(
	    Long id,
	    String username,
	    String email,
	    boolean suspendedByAccount,
	    boolean suspendedByAdmin,
	    boolean enabled
	) {
	    public static TenantUserSummaryResponse from(TenantUser u) {
	        boolean enabled = !u.isDeleted() && !u.isSuspendedByAccount() && !u.isSuspendedByAdmin();
	        return new TenantUserSummaryResponse(
	            u.getId(),
	            u.getUsername(),
	            u.getEmail(),
	            u.isSuspendedByAccount(),
	            u.isSuspendedByAdmin(),
	            enabled
	        );
	    }
	}
