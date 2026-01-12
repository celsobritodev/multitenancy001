package brito.com.multitenancy001.controlplane.api.mapper;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.shared.time.AppClock;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ControlPlaneUserApiMapper {

    private final AppClock appClock;

    public ControlPlaneUserApiMapper(AppClock appClock) {
        this.appClock = appClock;
    }

    public ControlPlaneAdminUserSummaryResponse toAdminSummary(ControlPlaneUser user) {
        LocalDateTime now = appClock.now();
        boolean enabled = user.isEnabledForLogin(now);

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
