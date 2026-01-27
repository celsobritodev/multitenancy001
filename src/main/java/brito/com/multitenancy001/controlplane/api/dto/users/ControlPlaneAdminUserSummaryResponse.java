package brito.com.multitenancy001.controlplane.api.dto.users;

public record ControlPlaneAdminUserSummaryResponse(
        Long id,

        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin
) {}
