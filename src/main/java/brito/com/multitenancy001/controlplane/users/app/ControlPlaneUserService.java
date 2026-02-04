package brito.com.multitenancy001.controlplane.users.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    // =========================================================
    // ADMIN ENDPOINTS (/api/admin/controlplane-users)
    // =========================================================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
        return publicUnitOfWork.tx(() -> {
            if (request == null) {
                throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            }

            // Sem o seu modelo completo (policy + password hashing + permission overrides),
            // não dá pra implementar com segurança aqui sem “inventar” regra.
            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "createControlPlaneUser() ainda não foi ligado ao modelo real (hash de senha, policy e permissões)",
                    501
            );
        });
    }

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return publicUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();

            // ✅ SEM depender de métodos custom no repository (para não quebrar compile):
            // usa findAll() e filtra por accountId (ControlPlane).
            return controlPlaneUserRepository.findAll().stream()
                    .filter(u -> u.getAccount() != null && u.getAccount().getId() != null && u.getAccount().getId().equals(cp.getId()))
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        return publicUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("USER_OUT_OF_SCOPE", "Usuário não pertence ao Control Plane", 403);
            }

            return mapToResponse(user);
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(Long userId, ControlPlaneUserUpdateRequest request) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            // Garantia de escopo (CP)
            getControlPlaneUser(userId);

            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "updateControlPlaneUser() ainda não foi ligado ao modelo real (campos mutáveis + validações + unicidade)",
                    501
            );
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId,
            ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            // Garantia de escopo (CP)
            getControlPlaneUser(userId);

            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "updateControlPlaneUserPermissions() ainda não foi ligado ao seu modelo de overrides/validação de escopo",
                    501
            );
        });
    }

    public void resetControlPlaneUserPassword(Long userId, ControlPlaneUserPasswordResetRequest request) {
        publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            // Garantia de escopo (CP)
            getControlPlaneUser(userId);

            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "resetControlPlaneUserPassword() depende do seu PasswordEncoder/policy e campos de senha no domínio",
                    501
            );
        });
    }

    public void softDeleteControlPlaneUser(Long userId) {
        publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            // Garantia de escopo (CP)
            getControlPlaneUser(userId);

            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "softDeleteControlPlaneUser() ainda não foi ligado ao seu SoftDelete padrão (deleted + audit.deletedAt)",
                    501
            );
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            // Garantia de escopo (CP)
            getControlPlaneUser(userId);

            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "restoreControlPlaneUser() ainda não foi ligado ao seu modelo de restore (deleted=false + audit.deletedAt=null)",
                    501
            );
        });
    }

    // =========================================================
    // ENABLED ENDPOINTS (já existiam no seu service)
    // =========================================================

    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            List<ControlPlaneUser> users =
                    controlPlaneUserRepository.findEnabledByAccountId(controlPlaneAccount.getId());

            return users.stream().map(this::mapToResponse).toList();
        });
    }

    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account controlPlaneAccount = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findEnabledByIdAndAccountId(userId, controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_ENABLED",
                            "Usuário não encontrado ou não habilitado",
                            404
                    ));

            return mapToResponse(user);
        });
    }

    // =========================================================
    // ME ENDPOINTS (/api/controlplane/me)
    // =========================================================

    public ControlPlaneMeResponse getMe() {
        return publicUnitOfWork.readOnly(() -> {
            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "getMe() ainda não foi ligado ao SecurityContext/SecurityUtils do ControlPlane",
                    501
            );
        });
    }

    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest request) {
        publicUnitOfWork.tx(() -> {
            if (request == null) {
                throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            }
            throw new ApiException(
                    "NOT_IMPLEMENTED",
                    "changeMyPassword() ainda não foi ligado ao SecurityContext + política de senha do ControlPlane",
                    501
            );
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ControlPlaneUserDetailsResponse mapToResponse(ControlPlaneUser user) {
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

    private Account getControlPlaneAccount() {
        return accountRepository.findControlPlaneAccount()
                .orElseThrow(() -> new ApiException(
                        "CONTROLPLANE_ACCOUNT_NOT_FOUND",
                        "Conta controlplane não encontrada",
                        500
                ));
    }
}
