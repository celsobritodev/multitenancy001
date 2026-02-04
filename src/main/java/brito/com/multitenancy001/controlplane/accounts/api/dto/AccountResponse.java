package brito.com.multitenancy001.controlplane.accounts.api.dto;

import java.time.Instant;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountType;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;

public record AccountResponse(
        Long id,
        String displayName,
        String slug,
        String schemaName,
        AccountStatus status,
        AccountType accountType,
        SubscriptionPlan subscriptionPlan,
        Instant createdAt,
        Instant trialEndDate
) {}

