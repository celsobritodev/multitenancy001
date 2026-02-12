package brito.com.multitenancy001.controlplane.accounts.app.dto;

import java.time.Instant;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;

public record AccountStatusChangeResponse(
        Long accountId,
        AccountStatus newStatus,
        AccountStatus previousStatus,
        Instant changedAt,
        String tenantSchema,
        boolean tenantUsersUpdated,
        AccountStatusSideEffect action,
        int affectedUsers
) {
    public static AccountStatusChangeResponse from(AccountStatusChangeResult r) {
        return new AccountStatusChangeResponse(
                r.accountId(),
                r.newStatus(),
                r.previousStatus(),
                r.changedAt(),
                r.tenantSchema(),
                r.tenantUsersUpdated(),
                r.action(),
                r.affectedUsers()
        );
    }
}
