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
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import brito.com.multitenancy001.shared.validation.TextValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de senha de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Reset administrativo de senha.</li>
 *   <li>Troca da própria senha pelo usuário autenticado.</li>
 *   <li>Validação centralizada via validators.</li>
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
    private final ControlPlaneUserInternalFacade controlPlaneUserInternalFacade;

    public void resetControlPlaneUserPassword(
            Long userId,
            ControlPlaneUserPasswordResetRequest request
    ) {
        log.info("resetControlPlaneUserPassword INICIANDO | userId={}", userId);

        publicSchemaUnitOfWork.tx(() -> {

            ControlPlaneUserInternalFacade.AuditActor actor =
                    controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            // 🔥 VALIDAÇÃO CENTRALIZADA
            RequiredValidator.requireUserId(userId);
            RequiredValidator.requirePayload(request, ApiErrorCode.INVALID_REQUEST, "Requisição inválida");

            TextValidator.requireNewPassword(request.newPassword());
            TextValidator.requireNewPassword(request.confirmPassword());

            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException(
                        ApiErrorCode.PASSWORD_MISMATCH,
                        "Nova senha e confirmação não conferem"
                );
            }

            Account account = controlPlaneUserInternalFacade.getControlPlaneAccount();

            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadNotDeletedUserInControlPlane(userId, account.getId());

            controlPlaneUserInternalFacade.assertMutableUser(user);

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", "admin_reset"
            );

            controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    actor,
                    target,
                    account.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        String hash = passwordEncoder.encode(request.newPassword());
                        user.setTemporaryPasswordHash(hash);
                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            log.info("✅ resetControlPlaneUserPassword CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest request) {
        log.info("changeMyPassword INICIANDO");

        publicSchemaUnitOfWork.tx(() -> {

            ControlPlaneUserInternalFacade.AuditActor actor =
                    controlPlaneUserInternalFacade.resolveActorOrAnonymous();

            // 🔥 VALIDAÇÃO CENTRALIZADA
            RequiredValidator.requirePayload(request, ApiErrorCode.INVALID_REQUEST, "Requisição inválida");

            TextValidator.requirePassword(request.currentPassword());
            TextValidator.requireNewPassword(request.newPassword());
            TextValidator.requireNewPassword(request.confirmPassword());

            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException(
                        ApiErrorCode.PASSWORD_MISMATCH,
                        "Nova senha e confirmação não conferem"
                );
            }

            Long accountId = controlPlaneRequestIdentityService.getCurrentAccountId();
            Long userId = controlPlaneRequestIdentityService.getCurrentUserId();

            RequiredValidator.requireAccountId(accountId);
            RequiredValidator.requireUserId(userId);

            Account account = controlPlaneUserInternalFacade.getControlPlaneAccount();

            if (!account.getId().equals(accountId)) {
                throw new ApiException(
                        ApiErrorCode.FORBIDDEN,
                        "Usuário não pertence ao Control Plane"
                );
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.USER_NOT_FOUND,
                            "Usuário não encontrado"
                    ));

            if (!user.isEnabled()) {
                throw new ApiException(
                        ApiErrorCode.USER_NOT_ENABLED,
                        "Usuário não está habilitado para trocar senha"
                );
            }

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = controlPlaneUserInternalFacade.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
                    "reason", "self_change"
            );

            controlPlaneUserInternalFacade.auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    target,
                    account.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        String currentHash = user.getPassword();

                        if (currentHash == null ||
                                !passwordEncoder.matches(request.currentPassword(), currentHash)) {
                            throw new ApiException(
                                    ApiErrorCode.CURRENT_PASSWORD_INVALID,
                                    "Senha atual inválida"
                            );
                        }

                        String newHash = passwordEncoder.encode(request.newPassword());
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