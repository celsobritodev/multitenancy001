package brito.com.multitenancy001.controlplane.api.dto.users;

public record ControlPlaneAdminUserSummaryResponse(
        Long id,
        String username,
        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {
   
}
