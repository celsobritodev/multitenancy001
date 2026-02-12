package brito.com.multitenancy001.controlplane.accounts.api.mapper;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountApiMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getDisplayName(),
                account.getSlug(),
                account.getTenantSchema(),
                account.getStatus(),
                account.getType(),
                account.getSubscriptionPlan(),
                account.getAudit() != null ? account.getAudit().getCreatedAt() : null,
                account.getTrialEndAt()
        );
    }
}
