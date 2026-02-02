package brito.com.multitenancy001.controlplane.accounts.app.dto;

import java.time.LocalDateTime;

public record AccountStatusChangeResult(
        Long accountId,
        String newStatus,
        String previousStatus,
        LocalDateTime changedAt,
        String tenantSchema,
        boolean tenantUsersUpdated,
        String action,
        int affectedUsers
) {}
