package brito.com.multitenancy001.controlplane.api.dto.users;

import java.time.LocalDateTime;

public record ControlPlaneUserDetailsResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        String role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled,
        LocalDateTime createdAt
) {}
