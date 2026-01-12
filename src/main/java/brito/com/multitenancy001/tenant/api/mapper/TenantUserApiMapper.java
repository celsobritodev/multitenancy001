package brito.com.multitenancy001.tenant.api.mapper;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.api.dto.users.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;

@Component
public class TenantUserApiMapper {

    public TenantUserSummaryResponse toSummary(TenantUser tenantUser) {
        boolean enabled =
                !tenantUser.isDeleted()
                        && !tenantUser.isSuspendedByAccount()
                        && !tenantUser.isSuspendedByAdmin();

        return new TenantUserSummaryResponse(
                tenantUser.getId(),
                tenantUser.getUsername(),
                tenantUser.getEmail(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }

    public TenantUserDetailsResponse toDetails(TenantUser tenantUser) {
        boolean enabled =
                !tenantUser.isDeleted()
                        && !tenantUser.isSuspendedByAccount()
                        && !tenantUser.isSuspendedByAdmin();

        return new TenantUserDetailsResponse(
                tenantUser.getId(),
                tenantUser.getAccountId(),
                tenantUser.getName(),
                tenantUser.getUsername(),
                tenantUser.getEmail(),
                tenantUser.getRole() != null ? tenantUser.getRole().name() : null,
                tenantUser.getPhone(),
                tenantUser.getAvatarUrl(),
                tenantUser.getTimezone(),
                tenantUser.getLocale(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                tenantUser.isDeleted(),
                enabled
        );
    }
}
