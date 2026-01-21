package brito.com.multitenancy001.controlplane.api.mapper;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountApiMapper {

    private final ControlPlaneUserApiMapper controlPlaneUserApiMapper;

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getDisplayName(),
                account.getSlug(),
                account.getSchemaName(),
                account.getStatus(),            // ✅ enum direto
                account.getType(),              // ✅ enum direto
                account.getSubscriptionPlan(),  // ✅ enum direto
                account.getCreatedAt(),
                account.getTrialEndDate(),
                null
        );
    }

    public AccountResponse toResponse(Account account, ControlPlaneUser adminUser) {
        ControlPlaneAdminUserSummaryResponse adminResponse =
                adminUser != null ? controlPlaneUserApiMapper.toAdminSummary(adminUser) : null;

        return new AccountResponse(
                account.getId(),
                account.getDisplayName(),
                account.getSlug(),
                account.getSchemaName(),
                account.getStatus(),            // ✅ enum direto
                account.getType(),              // ✅ enum direto
                account.getSubscriptionPlan(),  // ✅ enum direto
                account.getCreatedAt(),
                account.getTrialEndDate(),
                adminResponse
        );
    }
}
