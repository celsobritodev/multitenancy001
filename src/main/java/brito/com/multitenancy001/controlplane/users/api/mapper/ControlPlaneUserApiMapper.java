package brito.com.multitenancy001.controlplane.users.api.mapper;

import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;

import org.springframework.stereotype.Component;

@Component
public class ControlPlaneUserApiMapper {

    public ControlPlaneAdminUserSummaryResponse toAdminSummary(ControlPlaneUser controlPlaneUser) {
        return new ControlPlaneAdminUserSummaryResponse(
                controlPlaneUser.getId(),
   
                controlPlaneUser.getEmail(),
                controlPlaneUser.isSuspendedByAccount(),
                controlPlaneUser.isSuspendedByAdmin()
        );
    }
}
