package brito.com.multitenancy001.controlplane.accounts.api.dto;

import java.time.LocalDateTime;

public record AccountStatusChangeResponse(
        Long id,
        String status,
        String previousStatus,
        LocalDateTime effectiveAt,
        String schemaName,
        SideEffects sideEffects
) {
    public record SideEffects(
            boolean tenantUsersUpdated,
            String action,     // "SUSPEND_BY_ACCOUNT" | "UNSUSPEND_BY_ACCOUNT" | "CANCELLED" | "NONE"
            int tenantUsersCount
    ) {}
}
