package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de comandos de usuários do Control Plane.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com os chamadores atuais.</li>
 *   <li>Delegar casos de uso para serviços especializados.</li>
 *   <li>Evitar concentração de múltiplos fluxos de mutação em um único bean.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserCommandFacade {

    private final ControlPlaneUserCreateCommandService controlPlaneUserCreateCommandService;
    private final ControlPlaneUserUpdateCommandService controlPlaneUserUpdateCommandService;
    private final ControlPlaneUserPermissionsCommandService controlPlaneUserPermissionsCommandService;

    /**
     * Cria usuário do Control Plane.
     *
     * @param controlPlaneUserCreateRequest request de criação
     * @return usuário criado
     */
    public ControlPlaneUserDetailsResponse createControlPlaneUser(
            ControlPlaneUserCreateRequest controlPlaneUserCreateRequest
    ) {
        log.debug(
                "CONTROL_PLANE_USER_COMMAND_FACADE_CREATE | email={}",
                controlPlaneUserCreateRequest != null ? controlPlaneUserCreateRequest.email() : null
        );
        return controlPlaneUserCreateCommandService.createControlPlaneUser(controlPlaneUserCreateRequest);
    }

    /**
     * Atualiza dados de usuário do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneUserUpdateRequest request de atualização
     * @return usuário atualizado
     */
    public ControlPlaneUserDetailsResponse updateControlPlaneUser(
            Long userId,
            ControlPlaneUserUpdateRequest controlPlaneUserUpdateRequest
    ) {
        log.debug("CONTROL_PLANE_USER_COMMAND_FACADE_UPDATE | userId={}", userId);
        return controlPlaneUserUpdateCommandService.updateControlPlaneUser(userId, controlPlaneUserUpdateRequest);
    }

    /**
     * Atualiza permissões explícitas de usuário.
     *
     * @param userId id do usuário
     * @param controlPlaneUserPermissionsUpdateRequest request de permissões
     * @return usuário atualizado
     */
    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId,
            ControlPlaneUserPermissionsUpdateRequest controlPlaneUserPermissionsUpdateRequest
    ) {
        log.debug("CONTROL_PLANE_USER_COMMAND_FACADE_UPDATE_PERMISSIONS | userId={}", userId);
        return controlPlaneUserPermissionsCommandService.updateControlPlaneUserPermissions(
                userId,
                controlPlaneUserPermissionsUpdateRequest
        );
    }
}