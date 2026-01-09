package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.tenant.domain.user.TenantUser;

public record AccountUserSummaryResponse(
	    Long id,
	    String username,
	    String email,
	    boolean suspendedByAccount,
	    boolean suspendedByAdmin,
	    boolean enabled
	) {
	    public static AccountUserSummaryResponse from(TenantUser u) {
	        boolean enabled = !u.isDeleted() && !u.isSuspendedByAccount() && !u.isSuspendedByAdmin();
	        return new AccountUserSummaryResponse(
	            u.getId(),
	            u.getUsername(),
	            u.getEmail(),
	            u.isSuspendedByAccount(),
	            u.isSuspendedByAdmin(),
	            enabled
	        );
	    }
	}
