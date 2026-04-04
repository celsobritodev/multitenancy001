package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.SystemRoleName;

/**
 * Componente responsável pelo mapeamento de respostas do módulo de usuários do Control Plane.
 */
@Component
public class ControlPlaneUserApiResponseMapper {

    /**
     * Mapeia usuário do domínio para DTO de detalhes.
     *
     * @param user usuário do domínio
     * @return DTO de detalhes
     */
    public ControlPlaneUserDetailsResponse mapToDetailsResponse(ControlPlaneUser user) {
        return new ControlPlaneUserDetailsResponse(
                user.getId(),
                user.getAccount().getId(),
                user.getName(),
                user.getEmail(),
                SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.isEnabled(),
                user.getAudit() == null ? null : user.getAudit().getCreatedAt()
        );
    }

    /**
     * Mapeia usuário do domínio para DTO do endpoint /me.
     *
     * @param user usuário autenticado
     * @return DTO /me
     */
    public ControlPlaneMeResponse mapToMeResponse(ControlPlaneUser user) {
        return new ControlPlaneMeResponse(
                user.getId(),
                user.getAccount().getId(),
                user.getName(),
                user.getEmail(),
                SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.isEnabled()
        );
    }
}