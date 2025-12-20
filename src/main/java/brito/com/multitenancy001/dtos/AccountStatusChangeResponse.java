package brito.com.multitenancy001.dtos;

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
            boolean tenantUsersSuspended,
            int tenantUsersCount
    ) {}
}
