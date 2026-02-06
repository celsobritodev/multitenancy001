package brito.com.multitenancy001.controlplane.users.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.*;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private static final String BUILTIN_IMMUTABLE_CODE = "USER_BUILT_IN_IMMUTABLE";
    private static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ser alterado/deletado/restaurado; apenas senha pode ser trocada.";

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    private final SecurityUtils securityUtils;
    private final ControlPlaneUserExplicitPermissionsService explicitPermissionsService;

    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    // =========================================================
    // ADMIN ENDPOINTS (/api/admin/controlplane-users)
    // =========================================================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
        return publicUnitOfWork.tx(() -> {
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            if (request.role() == null) throw new ApiException("ROLE_REQUIRED", "role é obrigatório", 400);
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

            // Admin cria usuário com senha temporária por padrão
            user.setTemporaryPasswordHash(hash);

            ControlPlaneUser saved = controlPlaneUserRepository.save(user);

            // Permissões explícitas (opcional)
            if (request.permissions() != null && !request.permissions().isEmpty()) {
                explicitPermissionsService.setExplicitPermissionsFromCodes(saved.getId(), request.permissions());
            }

            // ✅ garante identity CP para login por email
            loginIdentityProvisioningService.ensureControlPlaneIdentity(email);

            return getControlPlaneUser(saved.getId());
        });
    }

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return publicUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();

            return controlPlaneUserRepository.findNotDeletedByAccountId(cp.getId()).stream()
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

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            // name
            if (request.name() != null) {
                user.rename(normalizeNameOrThrow(request.name()));
            }

            // email
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

                    // ✅ você pediu: trocar o EMAIL_MUTATION_NOT_SUPPORTED por:
                    user.changeEmail(newEmail);

                    // ✅ mantém login_identities CP em sincronia
                    loginIdentityProvisioningService.moveControlPlaneIdentity(currentEmail, newEmail);
                }
            }

            // role
            if (request.role() != null) {
                user.changeRole(request.role());
            }

            controlPlaneUserRepository.save(user);

            // permissions (opcional)
            if (request.permissions() != null) {
                explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());
            }

            return getControlPlaneUser(userId);
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(Long userId, ControlPlaneUserPermissionsUpdateRequest request) {
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

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            String hash = passwordEncoder.encode(request.newPassword());

            // padrão admin reset = temporária
            user.setTemporaryPasswordHash(hash);

            controlPlaneUserRepository.save(user);
            return null;
        });
    }

    public void softDeleteControlPlaneUser(Long userId) {
        publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            user.softDelete();
            controlPlaneUserRepository.save(user);

            // opcional: pode remover a identity ao deletar (aqui eu NÃO removi para evitar lock-out acidental)
            // loginIdentityProvisioningService.deleteControlPlaneIdentity(user.getEmail());

            return null;
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        return publicUnitOfWork.tx(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("USER_OUT_OF_SCOPE", "Usuário não pertence ao Control Plane", 403);
            }

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            user.restore();
            controlPlaneUserRepository.save(user);

            // garante identity ao restaurar
            loginIdentityProvisioningService.ensureControlPlaneIdentity(user.getEmail());

            return mapToResponse(user);
        });
    }

    // =========================================================
    // ENABLED ENDPOINTS
    // =========================================================

    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        return publicUnitOfWork.readOnly(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();
            return controlPlaneUserRepository.findEnabledByAccountId(controlPlaneAccount.getId())
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        return publicUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account controlPlaneAccount = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findEnabledByIdAndAccountId(userId, controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_ENABLED", "Usuário não encontrado ou não habilitado", 404));

            return mapToResponse(user);
        });
    }

    // =========================================================
    // ME ENDPOINTS (/api/controlplane/me)
    // =========================================================

    public ControlPlaneMeResponse getMe() {
        return publicUnitOfWork.readOnly(() -> {
            Long accountId = securityUtils.getCurrentAccountId();
            Long userId = securityUtils.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || !cp.getId().equals(accountId)) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

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
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
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

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

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

    // =========================================================
    // Helpers
    // =========================================================

    private static String normalizeEmailOrThrow(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (email == null) throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
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
                .orElseThrow(() -> new ApiException("CONTROLPLANE_ACCOUNT_NOT_FOUND", "Conta controlplane não encontrada", 500));
    }
}
