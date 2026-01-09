package brito.com.multitenancy001.controlplane.api.mapper;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

@Component
public class ControlPlaneUserApiMapper {

    private final Clock clock;

    public ControlPlaneUserApiMapper(Clock clock) {
        this.clock = clock;
    }

    public ControlPlaneAdminUserSummaryResponse toAdminSummary(ControlPlaneUser user) {
        LocalDateTime now = LocalDateTime.now(clock);
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
