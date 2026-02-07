package brito.com.multitenancy001.tenant.users.api.mapper;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.api.dto.TenantActorRef;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class TenantUserApiMapper {

	private static SystemRoleName toSystemRoleOrNull(TenantRole tenantRole) {
	    if (tenantRole == null) return null;

	    return switch (tenantRole) {
	        case TENANT_OWNER -> SystemRoleName.TENANT_OWNER;
	        case TENANT_ADMIN -> SystemRoleName.TENANT_ADMIN;
	        case TENANT_SUPPORT -> SystemRoleName.TENANT_SUPPORT;
	        case TENANT_USER -> SystemRoleName.TENANT_USER;
	        case TENANT_PRODUCT_MANAGER -> SystemRoleName.TENANT_PRODUCT_MANAGER;
	        case TENANT_SALES_MANAGER -> SystemRoleName.TENANT_SALES_MANAGER;
	        case TENANT_BILLING_MANAGER -> SystemRoleName.TENANT_BILLING_MANAGER;
	        case TENANT_READ_ONLY -> SystemRoleName.TENANT_READ_ONLY;
	        case TENANT_OPERATOR -> SystemRoleName.TENANT_OPERATOR;
	    };
	}


    public TenantUserSummaryResponse toSummary(TenantUser tenantUser) {
        boolean enabled =
                !tenantUser.isDeleted()
                        && !tenantUser.isSuspendedByAccount()
                        && !tenantUser.isSuspendedByAdmin();

        return new TenantUserSummaryResponse(
                tenantUser.getId(),
                tenantUser.getEmail(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }

    public TenantMeResponse toMe(TenantUser u) {
        boolean enabled = u.isEnabled();
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantMeResponse(
                u.getId(),
                u.getAccountId(),
                u.getName(),
                u.getEmail(),
                role,
                u.getPhone(),
                u.getAvatarUrl(),
                u.getTimezone(),
                u.getLocale(),
                u.isMustChangePassword(),
                u.getOrigin(),
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                u.isDeleted(),
                enabled
        );
    }

    public TenantUserDetailsResponse toDetails(TenantUser u) {
        boolean enabled = u.isEnabled();
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserDetailsResponse(
                u.getId(),
                u.getAccountId(),
                u.getName(),
                u.getEmail(),
                role,
                u.getPhone(),
                u.getAvatarUrl(),
                u.getTimezone(),
                u.getLocale(),
                u.isMustChangePassword(),
                u.getOrigin(),
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                u.isDeleted(),
                enabled
        );
    }

    public TenantUserListItemResponse toListItemBasic(TenantUser u) {
        boolean enabled = u.isEnabled();
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserListItemResponse(
                u.getId(),
                u.getEmail(),
                role,
                List.of(),
                u.isMustChangePassword(),
                u.getOrigin(),
                null,
                null,
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                enabled
        );
    }

    public TenantUserListItemResponse toListItemRich(TenantUser u) {
        boolean enabled = u.isEnabled();

        List<String> perms = u.getPermissions().stream()
                .filter(p -> p != null)
                .map(TenantPermission::name)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .sorted(Comparator.naturalOrder())
                .toList();

        TenantActorRef createdBy = mapCreatedBy(u.getAudit());
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserListItemResponse(
                u.getId(),
                u.getEmail(),
                role,
                perms,
                u.isMustChangePassword(),
                u.getOrigin(),
                u.getLastLoginAt(),
                createdBy,
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                enabled
        );
    }

    private TenantActorRef mapCreatedBy(AuditInfo audit) {
        if (audit == null) return null;
        if (audit.getCreatedBy() == null && audit.getCreatedByEmail() == null) return null;
        return new TenantActorRef(audit.getCreatedBy(), audit.getCreatedByEmail());
    }
}
