package brito.com.multitenancy001.controlplane.accounts.api.mapper;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.accounts.api.dto.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;

@Component
public class AccountUserApiMapper {

    public AccountTenantUserSummaryResponse toAccountUserSummary(UserSummaryData user) {

        boolean enabled =
                !user.deleted()
                && !user.suspendedByAccount()
                && !user.suspendedByAdmin();

        return new AccountTenantUserSummaryResponse(
                user.id(),
                user.accountId(),
                user.name(),
                user.email(),
                user.role(),
                user.suspendedByAccount(),
                user.suspendedByAdmin(),
                enabled
        );
    }
}
