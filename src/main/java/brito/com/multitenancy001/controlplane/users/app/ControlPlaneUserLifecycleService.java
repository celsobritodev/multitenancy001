package brito.com.multitenancy001.controlplane.users.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserSuspendRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de lifecycle de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Soft delete.</li>
 *   <li>Restore de soft delete.</li>
 *   <li>Suspensão administrativa.</li>
 *   <li>Restauração administrativa.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserLifecycleService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneUserInternalFacade controlPlaneUserInternalFacade;
    private final ControlPlaneUserIdentitySyncService controlPlaneUserIdentitySyncService;

    /**
     * Restaura usuário soft-deleted do Control Plane.
     *
     * @param userId id do usuário
     * @return usuário restaurado
     */
    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        log.info("restoreControlPlaneUser INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user = controlPlaneUserInternalFacade.loadUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", "soft_restore"
            );

            ControlPlaneUserDetailsResponse response = controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_RESTORED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        user.restore();
                        ControlPlaneUser saved = controlPlaneUserRepository.save(user);

                        controlPlaneUserIdentitySyncService.ensureControlPlaneIdentityNow(
                                saved.getEmail(),
                                saved.getId(),
                                "restore"
                        );

                        return controlPlaneUserInternalFacade.mapToDetailsResponse(saved);
                    }
            );

            log.info("✅ restoreControlPlaneUser CONCLUÍDO | userId={}", userId);
            return response;
        });
    }

    /**
     * Realiza soft delete de usuário do Control Plane.
     *
     * @param userId id do usuário
     */
    public void softDeleteControlPlaneUser(Long userId) {
        log.info("softDeleteControlPlaneUser INICIANDO | userId={}", userId);

        publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadNotDeletedUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            final Long deletedUserId = user.getId();

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", "soft_delete"
            );

            controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_DELETED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        user.softDelete();
                        controlPlaneUserRepository.save(user);

                        controlPlaneUserIdentitySyncService.deleteControlPlaneIdentityNow(
                                deletedUserId,
                                "softDelete"
                        );

                        return null;
                    }
            );

            log.info("✅ softDeleteControlPlaneUser CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    /**
     * Suspende usuário por ação administrativa.
     *
     * @param userId id do usuário
     * @param controlPlaneUserSuspendRequest request opcional
     */
    public void suspendControlPlaneUserByAdmin(
            Long userId,
            ControlPlaneUserSuspendRequest controlPlaneUserSuspendRequest
    ) {
        log.info("suspendControlPlaneUserByAdmin INICIANDO | userId={}", userId);

        publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadNotDeletedUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", controlPlaneUserSuspendRequest == null ? null : controlPlaneUserSuspendRequest.reason(),
                    "by", "admin",
                    "suspendedByAdminBefore", user.isSuspendedByAdmin(),
                    "suspendedByAccount", user.isSuspendedByAccount()
            );

            Map<String, Object> success = new LinkedHashMap<>(attempt);

            controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SUSPENDED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> success,
                    () -> {
                        user.suspendByAdmin();
                        controlPlaneUserRepository.save(user);

                        success.put("suspendedByAdminAfter", user.isSuspendedByAdmin());
                        success.put("enabledAfter", user.isEnabled());

                        return null;
                    }
            );

            log.info("✅ suspendControlPlaneUserByAdmin CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    /**
     * Remove suspensão administrativa do usuário.
     *
     * @param userId id do usuário
     * @param controlPlaneUserSuspendRequest request opcional
     */
    public void restoreControlPlaneUserByAdmin(
            Long userId,
            ControlPlaneUserSuspendRequest controlPlaneUserSuspendRequest
    ) {
        log.info("restoreControlPlaneUserByAdmin INICIANDO | userId={}", userId);

        publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadNotDeletedUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", controlPlaneUserSuspendRequest == null ? null : controlPlaneUserSuspendRequest.reason(),
                    "by", "admin",
                    "suspendedByAdminBefore", user.isSuspendedByAdmin(),
                    "suspendedByAccount", user.isSuspendedByAccount()
            );

            Map<String, Object> success = new LinkedHashMap<>(attempt);

            controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_RESTORED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> success,
                    () -> {
                        user.unsuspendByAdmin();
                        controlPlaneUserRepository.save(user);

                        success.put("suspendedByAdminAfter", user.isSuspendedByAdmin());
                        success.put("enabledAfter", user.isEnabled());

                        return null;
                    }
            );

            log.info("✅ restoreControlPlaneUserByAdmin CONCLUÍDO | userId={}", userId);
            return null;
        });
    }
}