package brito.com.multitenancy001.controlplane.api.mapper;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import org.springframework.stereotype.Component;

@Component
public class ControlPlaneUserApiMapper {

    public ControlPlaneAdminUserSummaryResponse toAdminSummary(ControlPlaneUser user) {
        return new ControlPlaneAdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin()
        );
    }
}
