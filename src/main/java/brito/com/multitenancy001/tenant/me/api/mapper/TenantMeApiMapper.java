package brito.com.multitenancy001.tenant.me.api.mapper;

import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import org.springframework.stereotype.Component;

@Component
public class TenantMeApiMapper {

    public TenantMeResponse toMe(TenantUser tenantUser) {
        boolean enabled =
                !tenantUser.isDeleted()
                        && !tenantUser.isSuspendedByAccount()
                        && !tenantUser.isSuspendedByAdmin();

        return new TenantMeResponse(
                tenantUser.getId(),
                tenantUser.getAccountId(),
                tenantUser.getName(),
                tenantUser.getEmail(),
                tenantUser.getRole(),
                tenantUser.getPhone(),
                tenantUser.getAvatarUrl(),
                tenantUser.getTimezone(),
                tenantUser.getLocale(),
                tenantUser.isMustChangePassword(),
                tenantUser.getOrigin(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                tenantUser.isDeleted(),
                enabled
        );
    }
}
