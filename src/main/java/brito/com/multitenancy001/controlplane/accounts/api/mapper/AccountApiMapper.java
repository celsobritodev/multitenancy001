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
                account.getSchemaName(),
                account.getStatus(),            // enum direto
                account.getType(),              // enum direto
                account.getSubscriptionPlan(),  // enum direto
                account.getCreatedAt(),
                account.getTrialEndDate()
        );
    }
}
