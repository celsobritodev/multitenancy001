package brito.com.multitenancy001.controlplane.api.dto.users;

public record ControlPlaneMeResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        String role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled
) {}
