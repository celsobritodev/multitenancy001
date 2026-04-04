package brito.com.multitenancy001.controlplane.users.app;

import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de atualização de permissões explícitas de usuário do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar request de alteração de permissões.</li>
 *   <li>Carregar e validar mutabilidade do usuário.</li>
 *   <li>Persistir permissões explícitas.</li>
 *   <li>Registrar auditoria do fluxo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserPermissionsCommandService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserExplicitPermissionsService controlPlaneUserExplicitPermissionsService;
    private final ControlPlaneUserInternalFacade controlPlaneUserInternalFacade;

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
        log.info("updateControlPlaneUserPermissions INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            if (controlPlaneUserPermissionsUpdateRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            int permissionCount = controlPlaneUserPermissionsUpdateRequest.permissions() == null
                    ? 0
                    : controlPlaneUserPermissionsUpdateRequest.permissions().size();

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", "permissions_endpoint",
                    "permissionsCount", permissionCount
            );

            ControlPlaneUserDetailsResponse response = controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    null,
                    () -> {
                        controlPlaneUserExplicitPermissionsService.setExplicitPermissionsFromCodes(
                                userId,
                                controlPlaneUserPermissionsUpdateRequest.permissions()
                        );
                        return controlPlaneUserInternalFacade.mapToDetailsResponse(user);
                    }
            );

            log.info("✅ updateControlPlaneUserPermissions CONCLUÍDO | userId={}", userId);
            return response;
        });
    }
}