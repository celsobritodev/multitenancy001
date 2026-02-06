package brito.com.multitenancy001.controlplane.users.app;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    // ✅ wiring do SecurityContext
    private final SecurityUtils securityUtils;

    // ✅ wiring de overrides de permission (CP_*)
    private final ControlPlaneUserExplicitPermissionsService explicitPermissionsService;

    // ✅ senha + tempo (para alinhar com regra must_change_password/password_changed_at)
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    // =========================================================
    // ADMIN ENDPOINTS (/api/admin/controlplane-users)
    // =========================================================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
        return publicUnitOfWork.tx(() -> {
            if (request == null) {
                throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            }
            if (request.role() == null) {
                throw new ApiException("ROLE_REQUIRED", "role é obrigatório", 400);
            }
            if (request.password() == null || request.password().isBlank()) {
                throw new ApiException("INVALID_PASSWORD", "senha é obrigatória", 400);
            }

            Account cp = getControlPlaneAccount();

            String email = normalizeEmailOrThrow(request.email());
            if (ControlPlaneBuiltInUsers.isReservedEmail(email)) {
                throw new ApiException("EMAIL_RESERVED", "Este email é reservado do sistema (BUILT_IN)", 409);
            }

            boolean emailExists = controlPlaneUserRepository
                    .findByEmailAndAccount_IdAndDeletedFalse(email, cp.getId())
                    .isPresent();
            if (emailExists) {
                throw new ApiException("EMAIL_ALREADY_IN_USE", "Já existe um usuário ativo com este email", 409);
            }

            String name = normalizeNameOrThrow(request.name());
            ControlPlaneRole role = request.role();

            String hash = passwordEncoder.encode(request.password());

            ControlPlaneUser user = ControlPlaneUser.builder()
                    .account(cp)
                    .origin(EntityOrigin.ADMIN)
                    .name(name)
                    .email(email)
                    .role(role)
                    .build();

            // ✅ criação por admin = senha temporária
            user.setTemporaryPasswordHash(hash);

            ControlPlaneUser saved = controlPlaneUserRepository.save(user);

            if (request.permissions() != null && !request.permissions().isEmpty()) {
                explicitPermissionsService.setExplicitPermissionsFromCodes(saved.getId(), request.permissions());
            }

            return getControlPlaneUser(saved.getId());
        });
    }

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return publicUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();

            return controlPlaneUserRepository.findAll().stream()
                    .filter(u -> u.getAccount() != null
                            && u.getAccount().getId() != null
                            && u.getAccount().getId().equals(cp.getId()))
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

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findAnyByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isDeleted()) {
                throw new ApiException("USER_DELETED", "Usuário está deletado e não pode ser alterado", 409);
            }
            if (user.isBuiltInUser()) {
                throw new ApiException("USER_BUILT_IN_IMMUTABLE", "Usuário BUILT_IN não pode ser alterado via admin", 409);
            }

            if (request.name() != null) {
                String newName = normalizeNameOrThrow(request.name());
                user.rename(newName);
            }

            if (request.email() != null) {
                String newEmail = normalizeEmailOrThrow(request.email());

                if (ControlPlaneBuiltInUsers.isReservedEmail(newEmail)) {
                    throw new ApiException("EMAIL_RESERVED", "Este email é reservado do sistema (BUILT_IN)", 409);
                }

                String currentEmail = EmailNormalizer.normalizeOrNull(user.getEmail());
                if (currentEmail == null || !currentEmail.equals(newEmail)) {

                    boolean emailExists = controlPlaneUserRepository
                            .findByEmailAndAccount_IdAndDeletedFalse(newEmail, cp.getId())
                            .filter(u -> !u.getId().equals(user.getId()))
                            .isPresent();

                    if (emailExists) {
                        throw new ApiException("EMAIL_ALREADY_IN_USE", "Já existe um usuário ativo com este email", 409);
                    }

                    // ✅ agora sim: troca via método de domínio
                    user.changeEmail(newEmail);
                }
            }

            if (request.role() != null) {
                user.changeRole(request.role());
            }

            controlPlaneUserRepository.save(user);

            if (request.permissions() != null) {
                explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());
            }

            return getControlPlaneUser(userId);
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId,
            ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            getControlPlaneUser(userId);

            explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());

            return getControlPlaneUser(userId);
        });
    }

    public void resetControlPlaneUserPassword(Long userId, ControlPlaneUserPasswordResetRequest request) {
        publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            if (request.newPassword() == null || request.confirmPassword() == null) {
                throw new ApiException("INVALID_PASSWORD", "Senha e confirmação são obrigatórias", 400);
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException("PASSWORD_MISMATCH", "Nova senha e confirmação não conferem", 400);
            }

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findAnyByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isDeleted()) {
                throw new ApiException("USER_DELETED", "Usuário está deletado e não pode ter senha resetada", 409);
            }

            String hash = passwordEncoder.encode(request.newPassword());

            user.setTemporaryPasswordHash(hash);

            controlPlaneUserRepository.save(user);
            return null;
        });
    }

    public void softDeleteControlPlaneUser(Long userId) {
        publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findAnyByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            user.softDelete();

            controlPlaneUserRepository.save(user);
            return null;
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findAnyByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            user.restore();

            controlPlaneUserRepository.save(user);
            return mapToResponse(user);
        });
    }

    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        return publicUnitOfWork.readOnly(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            List<ControlPlaneUser> users =
                    controlPlaneUserRepository.findEnabledByAccountId(controlPlaneAccount.getId());

            return users.stream().map(this::mapToResponse).toList();
        });
    }

    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        return publicUnitOfWork.readOnly(() -> {
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

    public ControlPlaneMeResponse getMe() {
        return publicUnitOfWork.readOnly(() -> {
            Long accountId = securityUtils.getCurrentAccountId();
            Long userId = securityUtils.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || !cp.getId().equals(accountId)) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findAnyByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            return new ControlPlaneMeResponse(
                    user.getId(),
                    user.getAccount().getId(),
                    user.getName(),
                    user.getEmail(),
                    SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                    user.isSuspendedByAccount(),
                    user.isSuspendedByAdmin(),
                    user.isDeleted(),
                    user.isEnabled()
            );
        });
    }

    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest request) {
        publicUnitOfWork.tx(() -> {
            if (request == null) {
                throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            }
            if (request.currentPassword() == null || request.newPassword() == null || request.confirmPassword() == null) {
                throw new ApiException("INVALID_PASSWORD", "Senha atual, nova senha e confirmação são obrigatórias", 400);
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException("PASSWORD_MISMATCH", "Nova senha e confirmação não conferem", 400);
            }

            Long accountId = securityUtils.getCurrentAccountId();
            Long userId = securityUtils.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || userId == null || !cp.getId().equals(accountId)) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findAnyByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (!user.isEnabled()) {
                throw new ApiException("USER_NOT_ENABLED", "Usuário não está habilitado para trocar senha", 403);
            }

            String currentHash = user.getPassword();
            if (currentHash == null || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
                throw new ApiException("CURRENT_PASSWORD_INVALID", "Senha atual inválida", 400);
            }

            String newHash = passwordEncoder.encode(request.newPassword());

            user.changePasswordHash(newHash, appClock.instant());

            controlPlaneUserRepository.save(user);
            return null;
        });
    }

    private static String normalizeEmailOrThrow(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (email == null) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }
        return email;
    }

    private static String normalizeNameOrThrow(String raw) {
        if (raw == null) throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
        String name = raw.trim();
        if (name.isBlank()) throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
        return name;
    }

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
