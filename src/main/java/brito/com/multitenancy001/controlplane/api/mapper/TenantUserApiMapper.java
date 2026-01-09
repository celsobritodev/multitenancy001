package brito.com.multitenancy001.controlplane.api.mapper;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountUserSummaryResponse;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;

@Component
public class TenantUserApiMapper {

    public AccountUserSummaryResponse toAccountUserSummary(TenantUser u) {
        boolean enabled = !u.isDeleted()
                && !u.isSuspendedByAccount()
                && !u.isSuspendedByAdmin();

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
