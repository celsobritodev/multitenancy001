package brito.com.multitenancy001.controlplane.api.dto.accounts;

import java.time.LocalDateTime;

public record AccountStatusChangeResponse(
        Long id,
        String status,
        String previousStatus,
        LocalDateTime effectiveAt,
        String tenantSchema,
        SideEffects sideEffects
) {
    public record SideEffects(
            boolean tenantUsersUpdated,
            String action,     // "SUSPEND_BY_ACCOUNT" | "UNSUSPEND_BY_ACCOUNT" | "CANCELLED" | "NONE"
            int tenantUsersCount
    ) {}
}
