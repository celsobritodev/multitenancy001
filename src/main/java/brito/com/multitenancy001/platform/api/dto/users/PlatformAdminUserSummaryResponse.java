package brito.com.multitenancy001.platform.api.dto.users;

import brito.com.multitenancy001.platform.domain.user.PlatformUser;

public record PlatformAdminUserSummaryResponse(
        Long id,
        String username,
        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {
    public static PlatformAdminUserSummaryResponse from(PlatformUser user) {
        boolean enabled = user.isEnabledForLogin();
        return new PlatformAdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                enabled
        );
    }
}
