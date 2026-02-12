package brito.com.multitenancy001.controlplane.accounts.app.dto;

import java.time.Instant;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;

public record AccountStatusChangeResult(
        Long accountId,
        AccountStatus newStatus,
        AccountStatus previousStatus,
        Instant changedAt,
        String tenantSchema,
        boolean tenantUsersUpdated,
        AccountStatusSideEffect action,
        int affectedUsers
) {}
