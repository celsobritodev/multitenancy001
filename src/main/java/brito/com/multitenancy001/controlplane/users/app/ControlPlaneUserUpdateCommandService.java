package brito.com.multitenancy001.controlplane.users.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de atualização cadastral de usuário do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Atualizar nome, email e role.</li>
 *   <li>Atualizar permissões quando vierem no request de update.</li>
 *   <li>Sincronizar/mover identidade de login quando necessário.</li>
 *   <li>Registrar auditoria de update, role e permissões.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserUpdateCommandService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneUserExplicitPermissionsService controlPlaneUserExplicitPermissionsService;
    private final ControlPlaneUserInternalFacade controlPlaneUserInternalFacade;
    private final ControlPlaneUserIdentitySyncService controlPlaneUserIdentitySyncService;

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
        log.info("updateControlPlaneUser INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            if (controlPlaneUserUpdateRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadNotDeletedUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            String beforeName = user.getName();
            String beforeEmail = user.getEmail();
            ControlPlaneRole beforeRole = user.getRole();

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", "update",
                    "hasName", controlPlaneUserUpdateRequest.name() != null,
                    "hasEmail", controlPlaneUserUpdateRequest.email() != null,
                    "hasRole", controlPlaneUserUpdateRequest.role() != null,
                    "hasPermissions", controlPlaneUserUpdateRequest.permissions() != null
            );

            Map<String, Object> success = new LinkedHashMap<>();
            success.put("scope", ControlPlaneUserInternalFacade.SCOPE);
            success.put("reason", "update");

            return controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_UPDATED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> success,
                    () -> {
                        boolean roleChanged = false;
                        boolean permissionsChanged = controlPlaneUserUpdateRequest.permissions() != null;
                        Map<String, Object> changes = new LinkedHashMap<>();

                        if (controlPlaneUserUpdateRequest.name() != null) {
                            String newName =
                                    controlPlaneUserInternalFacade.normalizeNameOrThrow(controlPlaneUserUpdateRequest.name());

                            if (user.getName() == null || !user.getName().equals(newName)) {
                                user.rename(newName);
                                changes.put("nameBefore", beforeName);
                                changes.put("nameAfter", newName);
                            }
                        }

                        if (controlPlaneUserUpdateRequest.email() != null) {
                            String newEmail =
                                    controlPlaneUserInternalFacade.normalizeEmailOrThrow(controlPlaneUserUpdateRequest.email());

                            controlPlaneUserInternalFacade.validateNotReservedEmail(newEmail);

                            if (beforeEmail == null || !beforeEmail.equals(newEmail)) {
                                boolean emailExists = controlPlaneUserRepository
                                        .findByEmailAndAccount_IdAndDeletedFalse(newEmail, controlPlaneAccount.getId())
                                        .filter(existing -> !existing.getId().equals(user.getId()))
                                        .isPresent();

                                if (emailExists) {
                                    throw new ApiException(
                                            ApiErrorCode.EMAIL_ALREADY_IN_USE,
                                            "Já existe um usuário ativo com este email",
                                            409
                                    );
                                }

                                user.changeEmail(newEmail);

                                controlPlaneUserIdentitySyncService.moveControlPlaneIdentityNow(
                                        user.getId(),
                                        newEmail,
                                        "update"
                                );

                                changes.put("emailBefore", beforeEmail);
                                changes.put("emailAfter", newEmail);
                            }
                        }

                        if (controlPlaneUserUpdateRequest.role() != null) {
                            if (beforeRole == null || !beforeRole.equals(controlPlaneUserUpdateRequest.role())) {
                                user.changeRole(controlPlaneUserUpdateRequest.role());
                                roleChanged = true;

                                changes.put("roleBefore", beforeRole == null ? null : beforeRole.name());
                                changes.put("roleAfter", user.getRole() == null ? null : user.getRole().name());
                            }
                        }

                        controlPlaneUserRepository.save(user);

                        if (permissionsChanged) {
                            controlPlaneUserExplicitPermissionsService.setExplicitPermissionsFromCodes(
                                    userId,
                                    controlPlaneUserUpdateRequest.permissions()
                            );

                            controlPlaneUserInternalFacade.recordAudit(
                                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    user.getEmail(),
                                    user.getId(),
                                    controlPlaneAccount.getId(),
                                    null,
                                    controlPlaneUserInternalFacade.m(
                                            "scope", ControlPlaneUserInternalFacade.SCOPE,
                                            "reason", "update",
                                            "permissionsCount",
                                            controlPlaneUserUpdateRequest.permissions() == null
                                                    ? 0
                                                    : controlPlaneUserUpdateRequest.permissions().size(),
                                            "permissions", controlPlaneUserUpdateRequest.permissions()
                                    )
                            );

                            changes.put("permissionsChanged", true);
                            changes.put(
                                    "permissionsCount",
                                    controlPlaneUserUpdateRequest.permissions() == null
                                            ? 0
                                            : controlPlaneUserUpdateRequest.permissions().size()
                            );
                        }

                        if (roleChanged) {
                            controlPlaneUserInternalFacade.recordAudit(
                                    SecurityAuditActionType.ROLE_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    user.getEmail(),
                                    user.getId(),
                                    controlPlaneAccount.getId(),
                                    null,
                                    controlPlaneUserInternalFacade.m(
                                            "scope", ControlPlaneUserInternalFacade.SCOPE,
                                            "from", beforeRole == null ? null : beforeRole.name(),
                                            "to", user.getRole() == null ? null : user.getRole().name()
                                    )
                            );
                        }

                        success.put("changed", !changes.isEmpty());
                        success.put("changes", changes);

                        log.info("✅ updateControlPlaneUser CONCLUÍDO | userId={}", userId);
                        return controlPlaneUserInternalFacade.mapToDetailsResponse(user);
                    }
            );
        });
    }
}