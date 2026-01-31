package brito.com.multitenancy001.controlplane.users.api.dto;

public record ControlPlaneAdminUserSummaryResponse(
        Long id,

        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin
) {}
