package brito.com.multitenancy001.controlplane.users.app;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
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
 * Caso de uso de criação de usuário do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar request de criação.</li>
 *   <li>Validar unicidade de email no escopo do control plane.</li>
 *   <li>Criar usuário e senha temporária.</li>
 *   <li>Aplicar permissões explícitas iniciais, quando existirem.</li>
 *   <li>Sincronizar identidade de login.</li>
 *   <li>Registrar auditoria do fluxo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserCreateCommandService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneUserExplicitPermissionsService controlPlaneUserExplicitPermissionsService;
    private final PasswordEncoder passwordEncoder;
    private final ControlPlaneUserInternalFacade controlPlaneUserSupport;
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
        log.info(
                "🚀 createControlPlaneUser INICIANDO | email={}",
                controlPlaneUserCreateRequest != null ? controlPlaneUserCreateRequest.email() : null
        );

        return publicSchemaUnitOfWork.tx(() -> {
            ControlPlaneUserInternalFacade.AuditActor actor = controlPlaneUserSupport.resolveActorOrAnonymous();

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

            ControlPlaneUserInternalFacade.AuditTarget target =
                    new ControlPlaneUserInternalFacade.AuditTarget(email, null);

            Map<String, Object> attempt = controlPlaneUserSupport.m(
                    "scope", ControlPlaneUserInternalFacade.SCOPE,
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
                            "scope", ControlPlaneUserInternalFacade.SCOPE,
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
                                            "scope", ControlPlaneUserInternalFacade.SCOPE,
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

                        log.info(
                                "✅ createControlPlaneUser CONCLUÍDO | id={} email={}",
                                saved.getId(),
                                saved.getEmail()
                        );

                        return controlPlaneUserSupport.mapToDetailsResponse(saved);
                    }
            );
        });
    }
}