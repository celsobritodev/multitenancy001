package brito.com.multitenancy001.controlplane.users.app;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de senha de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Reset administrativo de senha.</li>
 *   <li>Troca da própria senha pelo usuário autenticado.</li>
 *   <li>Aplicar validações de senha e auditoria do fluxo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserPasswordService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneRequestIdentityService controlPlaneRequestIdentityService;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;
    private final ControlPlaneUserSupport controlPlaneUserSupport;

    /**
     * Reseta senha de um usuário do Control Plane por ação administrativa.
     *
     * @param userId id do usuário
     * @param controlPlaneUserPasswordResetRequest request de reset
     */
    public void resetControlPlaneUserPassword(
            Long userId,
            ControlPlaneUserPasswordResetRequest controlPlaneUserPasswordResetRequest
    ) {
        log.info("resetControlPlaneUserPassword INICIANDO | userId={}", userId);

        publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserSupport.AuditActor actor = controlPlaneUserSupport.resolveActorOrAnonymous();

            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            if (controlPlaneUserPasswordResetRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            if (controlPlaneUserPasswordResetRequest.newPassword() == null
                    || controlPlaneUserPasswordResetRequest.confirmPassword() == null) {
                throw new ApiException(
                        ApiErrorCode.INVALID_PASSWORD,
                        "Senha e confirmação são obrigatórias",
                        400
                );
            }

            if (!controlPlaneUserPasswordResetRequest.newPassword()
                    .equals(controlPlaneUserPasswordResetRequest.confirmPassword())) {
                throw new ApiException(
                        ApiErrorCode.PASSWORD_MISMATCH,
                        "Nova senha e confirmação não conferem",
                        400
                );
            }

            Account controlPlaneAccount = controlPlaneUserSupport.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserSupport.loadNotDeletedUserInControlPlane(userId, controlPlaneAccount.getId());

            controlPlaneUserSupport.assertMutableUser(user);

            ControlPlaneUserSupport.AuditTarget target =
                    new ControlPlaneUserSupport.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserSupport.m(
                    "scope", ControlPlaneUserSupport.SCOPE,
                    "reason", "admin_reset"
            );

            controlPlaneUserSupport.auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        String passwordHash = passwordEncoder.encode(controlPlaneUserPasswordResetRequest.newPassword());
                        user.setTemporaryPasswordHash(passwordHash);
                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            log.info("✅ resetControlPlaneUserPassword CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    /**
     * Altera a própria senha do usuário autenticado do Control Plane.
     *
     * @param controlPlaneChangeMyPasswordRequest request de troca
     */
    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest controlPlaneChangeMyPasswordRequest) {
        log.info("changeMyPassword INICIANDO");

        publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserSupport.AuditActor actor = controlPlaneUserSupport.resolveActorOrAnonymous();

            if (controlPlaneChangeMyPasswordRequest == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            }

            if (controlPlaneChangeMyPasswordRequest.currentPassword() == null
                    || controlPlaneChangeMyPasswordRequest.newPassword() == null
                    || controlPlaneChangeMyPasswordRequest.confirmPassword() == null) {
                throw new ApiException(
                        ApiErrorCode.INVALID_PASSWORD,
                        "Senha atual, nova senha e confirmação são obrigatórias",
                        400
                );
            }

            if (!controlPlaneChangeMyPasswordRequest.newPassword()
                    .equals(controlPlaneChangeMyPasswordRequest.confirmPassword())) {
                throw new ApiException(
                        ApiErrorCode.PASSWORD_MISMATCH,
                        "Nova senha e confirmação não conferem",
                        400
                );
            }

            Long currentAccountId = controlPlaneRequestIdentityService.getCurrentAccountId();
            Long currentUserId = controlPlaneRequestIdentityService.getCurrentUserId();

            Account controlPlaneAccount = controlPlaneUserSupport.getControlPlaneAccount();
            if (currentAccountId == null
                    || currentUserId == null
                    || !controlPlaneAccount.getId().equals(currentAccountId)) {
                throw new ApiException(
                        ApiErrorCode.FORBIDDEN,
                        "Usuário não pertence ao Control Plane",
                        403
                );
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(currentUserId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.USER_NOT_FOUND,
                            "Usuário não encontrado",
                            404
                    ));

            if (user.getAccount() == null
                    || user.getAccount().getId() == null
                    || !user.getAccount().getId().equals(controlPlaneAccount.getId())) {
                throw new ApiException(
                        ApiErrorCode.FORBIDDEN,
                        "Usuário não pertence ao Control Plane",
                        403
                );
            }

            if (!user.isEnabled()) {
                throw new ApiException(
                        ApiErrorCode.USER_NOT_ENABLED,
                        "Usuário não está habilitado para trocar senha",
                        403
                );
            }

            ControlPlaneUserSupport.AuditTarget target =
                    new ControlPlaneUserSupport.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserSupport.m(
                    "scope", ControlPlaneUserSupport.SCOPE,
                    "reason", "self_change"
            );

            controlPlaneUserSupport.auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    target,
                    controlPlaneAccount.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        String currentHash = user.getPassword();
                        if (currentHash == null
                                || !passwordEncoder.matches(
                                        controlPlaneChangeMyPasswordRequest.currentPassword(),
                                        currentHash
                                )) {
                            throw new ApiException(
                                    ApiErrorCode.CURRENT_PASSWORD_INVALID,
                                    "Senha atual inválida",
                                    400
                            );
                        }

                        String newHash = passwordEncoder.encode(controlPlaneChangeMyPasswordRequest.newPassword());
                        user.changePasswordHash(newHash, appClock.instant());

                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            log.info("✅ changeMyPassword CONCLUÍDO");
            return null;
        });
    }
}