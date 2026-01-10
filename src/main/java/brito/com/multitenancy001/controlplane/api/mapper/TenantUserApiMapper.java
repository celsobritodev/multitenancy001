package brito.com.multitenancy001.controlplane.api.mapper;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountUserSummaryResponse;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;

@Component
public class TenantUserApiMapper {

    public AccountUserSummaryResponse toAccountUserSummary(TenantUser tenantUser) {
        boolean enabled = !tenantUser.isDeleted()
                && !tenantUser.isSuspendedByAccount()
                && !tenantUser.isSuspendedByAdmin();

        return new AccountUserSummaryResponse(
                tenantUser.getId(),
                tenantUser.getUsername(),
                tenantUser.getEmail(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }
}
