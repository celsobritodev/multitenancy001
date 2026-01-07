package brito.com.multitenancy001.controlplane.api.dto.users;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

public record ControlPlaneAdminUserSummaryResponse(
        Long id,
        String username,
        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {
    public static ControlPlaneAdminUserSummaryResponse from(ControlPlaneUser user) {
        boolean enabled = user.isEnabledForLogin();
        return new ControlPlaneAdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                enabled
        );
    }
}
