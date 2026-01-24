package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.domain.account.AccountType;
import brito.com.multitenancy001.controlplane.domain.account.SubscriptionPlan;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String displayName,
        String slug,
        String schemaName,
        AccountStatus status,
        AccountType accountType,
        SubscriptionPlan subscriptionPlan,
        LocalDateTime createdAt,
        LocalDateTime trialEndDate
) {}
