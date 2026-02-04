package brito.com.multitenancy001.controlplane.accounts.app.dto;

import java.time.Instant;

public record AccountStatusChangeResult(
        Long accountId,
        String newStatus,
        String previousStatus,
        Instant changedAt,
        String tenantSchema,
        boolean tenantUsersUpdated,
        String action,
        int affectedUsers
) {}

