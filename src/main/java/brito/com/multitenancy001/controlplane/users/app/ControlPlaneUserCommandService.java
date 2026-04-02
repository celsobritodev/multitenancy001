package brito.com.multitenancy001.controlplane.users.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar usuário.</li>
 *   <li>Atualizar dados cadastrais de usuário.</li>
 *   <li>Atualizar permissões explícitas.</li>
 *   <li>Registrar auditoria de criação, alteração de role e alteração de permissões.</li>
 *   <li>Sincronizar identidade de login quando necessário.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserCommandService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneUserExplicitPermissionsService controlPlaneUserExplicitPermissionsService;
    private final PasswordEncoder passwordEncoder;
    private final ControlPlaneUserSupport controlPlaneUserSupport;
    private final ControlPlaneUserIdentitySyncService controlPlaneUserIdentitySyncService;

    /**
     * Cria usuário do Control Plane.
     *
     * @param controlPlaneUserCreateRequest request de criação
     * @return usuário criado
     */
    public ControlPlaneUserDetailsResponse createControlPlaneUser(
            ControlPlaneUserCreateRequest controlPlaneUserCreateRequest
    ) {
        log.info("🚀 createControlPlaneUser INICIANDO | email={}",
                controlPlaneUserCreateRequest != null ? controlPlaneUserCreateRequest.email() : null);

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserSupport.AuditActor actor = controlPlaneUserSupport.resolveActorOrAnonymous();

            if (controlPlaneUserCreateRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            if (controlPlaneUserCreateRequest.role() == null) {
                throw new ApiException(ApiErrorCode.ROLE_REQUIRED, "role é obrigatório", 400);
            }

            if (controlPlaneUserCreateRequest.password() == null
                    || controlPlaneUserCreateRequest.password().isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "senha é obrigatória", 400);
            }

            Account controlPlaneAccount = controlPlaneUserSupport.getControlPlaneAccount();

            String email = controlPlaneUserSupport.normalizeEmailOrThrow(controlPlaneUserCreateRequest.email());
            controlPlaneUserSupport.validateNotReservedEmail(email);

            ControlPlaneUserSupport.AuditTarget target =
                    new ControlPlaneUserSupport.AuditTarget(email, null);

            Map<String, Object> attempt = controlPlaneUserSupport.m(
                    "scope", ControlPlaneUserSupport.SCOPE,
                    "stage", "before_save",
                    "role", controlPlaneUserCreateRequest.role().name(),
                    "permissionsCount",
                    controlPlaneUserCreateRequest.permissions() == null
                            ? 0
                            : controlPlaneUserCreateRequest.permissions().size()
            );

            return controlPlaneUserSupport.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_CREATED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> controlPlaneUserSupport.m(
                            "scope", ControlPlaneUserSupport.SCOPE,
                            "stage", "after_save",
                            "role", controlPlaneUserCreateRequest.role().name(),
                            "permissionsCount",
                            controlPlaneUserCreateRequest.permissions() == null
                                    ? 0
                                    : controlPlaneUserCreateRequest.permissions().size()
                    ),
                    () -> {
                        boolean emailExists = controlPlaneUserRepository
                                .findByEmailAndAccount_IdAndDeletedFalse(email, controlPlaneAccount.getId())
                                .isPresent();

                        if (emailExists) {
                            throw new ApiException(
                                    ApiErrorCode.EMAIL_ALREADY_IN_USE,
                                    "Já existe um usuário ativo com este email",
                                    409
                            );
                        }

                        String name = controlPlaneUserSupport.normalizeNameOrThrow(controlPlaneUserCreateRequest.name());
                        ControlPlaneRole role = controlPlaneUserCreateRequest.role();
                        String passwordHash = passwordEncoder.encode(controlPlaneUserCreateRequest.password());

                        ControlPlaneUser user = ControlPlaneUser.builder()
                                .account(controlPlaneAccount)
                                .origin(EntityOrigin.ADMIN)
                                .name(name)
                                .email(email)
                                .role(role)
                                .build();

                        user.setTemporaryPasswordHash(passwordHash);

                        ControlPlaneUser saved = controlPlaneUserRepository.save(user);

                        if (controlPlaneUserCreateRequest.permissions() != null
                                && !controlPlaneUserCreateRequest.permissions().isEmpty()) {
                            controlPlaneUserExplicitPermissionsService.setExplicitPermissionsFromCodes(
                                    saved.getId(),
                                    controlPlaneUserCreateRequest.permissions()
                            );

                            controlPlaneUserSupport.recordAudit(
                                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    saved.getEmail(),
                                    saved.getId(),
                                    controlPlaneAccount.getId(),
                                    null,
                                    controlPlaneUserSupport.m(
                                            "scope", ControlPlaneUserSupport.SCOPE,
                                            "reason", "create",
                                            "permissionsCount", controlPlaneUserCreateRequest.permissions().size(),
                                            "permissions", controlPlaneUserCreateRequest.permissions()
                                    )
                            );
                        }

                        controlPlaneUserIdentitySyncService.ensureControlPlaneIdentityNow(
                                saved.getEmail(),
                                saved.getId(),
                                "create"
                        );

                        log.info("✅ createControlPlaneUser CONCLUÍDO | id={} email={}",
                                saved.getId(),
                                saved.getEmail());

                        return controlPlaneUserSupport.mapToDetailsResponse(saved);
                    }
            );
        });
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
        log.info("updateControlPlaneUser INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserSupport.AuditActor actor = controlPlaneUserSupport.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            if (controlPlaneUserUpdateRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserSupport.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserSupport.loadNotDeletedUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserSupport.assertMutableUser(user);

            String beforeName = user.getName();
            String beforeEmail = user.getEmail();
            ControlPlaneRole beforeRole = user.getRole();

            ControlPlaneUserSupport.AuditTarget target =
                    new ControlPlaneUserSupport.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserSupport.m(
                    "scope", ControlPlaneUserSupport.SCOPE,
                    "reason", "update",
                    "hasName", controlPlaneUserUpdateRequest.name() != null,
                    "hasEmail", controlPlaneUserUpdateRequest.email() != null,
                    "hasRole", controlPlaneUserUpdateRequest.role() != null,
                    "hasPermissions", controlPlaneUserUpdateRequest.permissions() != null
            );

            Map<String, Object> success = new LinkedHashMap<>();
            success.put("scope", ControlPlaneUserSupport.SCOPE);
            success.put("reason", "update");

            return controlPlaneUserSupport.auditAttemptSuccessFail(
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
                                    controlPlaneUserSupport.normalizeNameOrThrow(controlPlaneUserUpdateRequest.name());

                            if (user.getName() == null || !user.getName().equals(newName)) {
                                user.rename(newName);
                                changes.put("nameBefore", beforeName);
                                changes.put("nameAfter", newName);
                            }
                        }

                        if (controlPlaneUserUpdateRequest.email() != null) {
                            String newEmail =
                                    controlPlaneUserSupport.normalizeEmailOrThrow(controlPlaneUserUpdateRequest.email());

                            controlPlaneUserSupport.validateNotReservedEmail(newEmail);

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

                            controlPlaneUserSupport.recordAudit(
                                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    user.getEmail(),
                                    user.getId(),
                                    controlPlaneAccount.getId(),
                                    null,
                                    controlPlaneUserSupport.m(
                                            "scope", ControlPlaneUserSupport.SCOPE,
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
                            controlPlaneUserSupport.recordAudit(
                                    SecurityAuditActionType.ROLE_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    user.getEmail(),
                                    user.getId(),
                                    controlPlaneAccount.getId(),
                                    null,
                                    controlPlaneUserSupport.m(
                                            "scope", ControlPlaneUserSupport.SCOPE,
                                            "from", beforeRole == null ? null : beforeRole.name(),
                                            "to", user.getRole() == null ? null : user.getRole().name()
                                    )
                            );
                        }

                        success.put("changed", !changes.isEmpty());
                        success.put("changes", changes);

                        log.info("✅ updateControlPlaneUser CONCLUÍDO | userId={}", userId);
                        return controlPlaneUserSupport.mapToDetailsResponse(user);
                    }
            );
        });
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
        log.info("updateControlPlaneUserPermissions INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserSupport.AuditActor actor = controlPlaneUserSupport.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            if (controlPlaneUserPermissionsUpdateRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserSupport.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserSupport.loadUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserSupport.assertMutableUser(user);

            int permissionCount = controlPlaneUserPermissionsUpdateRequest.permissions() == null
                    ? 0
                    : controlPlaneUserPermissionsUpdateRequest.permissions().size();

            ControlPlaneUserSupport.AuditTarget target =
                    new ControlPlaneUserSupport.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserSupport.m(
                    "scope", ControlPlaneUserSupport.SCOPE,
                    "reason", "permissions_endpoint",
                    "permissionsCount", permissionCount
            );

            ControlPlaneUserDetailsResponse response = controlPlaneUserSupport.auditAttemptSuccessFail(
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
                        return controlPlaneUserSupport.mapToDetailsResponse(user);
                    }
            );

            log.info("✅ updateControlPlaneUserPermissions CONCLUÍDO | userId={}", userId);
            return response;
        });
    }
}