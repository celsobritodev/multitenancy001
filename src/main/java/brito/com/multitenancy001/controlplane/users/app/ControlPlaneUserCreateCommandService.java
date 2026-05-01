package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import brito.com.multitenancy001.shared.validation.TextValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de criação de usuário do Control Plane.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserCreateCommandService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneUserExplicitPermissionsService controlPlaneUserExplicitPermissionsService;
    private final PasswordEncoder passwordEncoder;
    private final ControlPlaneUserInternalFacade controlPlaneUserInternalFacade;
    private final ControlPlaneUserIdentitySyncService controlPlaneUserIdentitySyncService;

    public ControlPlaneUserDetailsResponse createControlPlaneUser(
            ControlPlaneUserCreateRequest request
    ) {
        log.info("🚀 createControlPlaneUser INICIANDO | email={}", request != null ? request.email() : null);

        return publicSchemaUnitOfWork.tx(() -> {

            

            // 🔥 VALIDAÇÕES CENTRALIZADAS
            RequiredValidator.requirePayload(
                    request,
                    ApiErrorCode.INVALID_REQUEST,
                    "Requisição inválida"
            );

            RequiredValidator.requireRole(request.role());

            TextValidator.requirePassword(request.password());

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();

            String email = controlPlaneUserInternalFacade.normalizeEmailOrThrow(request.email());
            controlPlaneUserInternalFacade.validateNotReservedEmail(email);

            boolean emailExists = controlPlaneUserRepository
                    .findByEmailAndAccount_IdAndDeletedFalse(email, controlPlaneAccount.getId())
                    .isPresent();

            if (emailExists) {
                throw new brito.com.multitenancy001.shared.kernel.error.ApiException(
                        ApiErrorCode.EMAIL_ALREADY_IN_USE,
                        "Já existe um usuário ativo com este email"
                );
            }

            String name = controlPlaneUserInternalFacade.normalizeNameOrThrow(request.name());
            ControlPlaneRole role = request.role();

            String passwordHash = passwordEncoder.encode(request.password());

            ControlPlaneUser user = ControlPlaneUser.builder()
                    .account(controlPlaneAccount)
                    .origin(EntityOrigin.ADMIN)
                    .name(name)
                    .email(email)
                    .role(role)
                    .build();

            user.setTemporaryPasswordHash(passwordHash);

            ControlPlaneUser saved = controlPlaneUserRepository.save(user);

            if (request.permissions() != null && !request.permissions().isEmpty()) {
                controlPlaneUserExplicitPermissionsService.setExplicitPermissionsFromCodes(
                        saved.getId(),
                        request.permissions()
                );
            }

            controlPlaneUserIdentitySyncService.ensureControlPlaneIdentityNow(
                    saved.getEmail(),
                    saved.getId(),
                    "create"
            );

            log.info("✅ createControlPlaneUser CONCLUÍDO | id={} email={}",
                    saved.getId(), saved.getEmail());

            return controlPlaneUserInternalFacade.mapToDetailsResponse(saved);
        });
    }
}