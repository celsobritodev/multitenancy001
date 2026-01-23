package brito.com.multitenancy001.controlplane.application.user;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUserOrigin;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.PermissionScopeValidator;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;
    private final SecurityUtils securityUtils;
    private final PublicExecutor publicExecutor;

    private static final Set<String> RESERVED_USERNAMES = Set.of("superadmin", "billing", "support", "operator");

    private Account getControlPlaneAccount() {
        return publicExecutor.run(() ->
                accountRepository.findBySlugAndDeletedFalse("controlplane")
                        .orElseThrow(() -> new ApiException(
                                "CONTROLPLANE_ACCOUNT_NOT_FOUND",
                                "Conta controlplane não encontrada. Rode a migration V4__insert_controlplane_account.sql",
                                500
                        ))
        );
    }

    private static final Set<ControlPlaneRole> OWNER_CAN_CREATE = EnumSet.of(
            ControlPlaneRole.CONTROLPLANE_SUPPORT,
            ControlPlaneRole.CONTROLPLANE_OPERATOR,
            ControlPlaneRole.CONTROLPLANE_BILLING_MANAGER
    );

    private static final Set<ControlPlaneRole> SUPPORT_CAN_CREATE = EnumSet.of(ControlPlaneRole.CONTROLPLANE_OPERATOR);

    private void assertCanCreateRole(ControlPlaneRole creatorRole, ControlPlaneRole targetRole) {
        if (creatorRole == null) throw new ApiException("FORBIDDEN", "Role do criador não encontrada", 403);
        if (targetRole == null) throw new ApiException("INVALID_ROLE", "Role alvo é obrigatória", 400);

        if (creatorRole == ControlPlaneRole.CONTROLPLANE_OWNER) {
            if (targetRole == ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("FORBIDDEN", "CONTROLPLANE_OWNER não pode criar outro CONTROLPLANE_OWNER", 403);
            }
            if (!OWNER_CAN_CREATE.contains(targetRole)) {
                throw new ApiException("FORBIDDEN", "CONTROLPLANE_OWNER não pode criar a role: " + targetRole, 403);
            }
            return;
        }

        if (creatorRole == ControlPlaneRole.CONTROLPLANE_SUPPORT) {
            if (targetRole == ControlPlaneRole.CONTROLPLANE_SUPPORT) {
                throw new ApiException("FORBIDDEN", "CONTROLPLANE_SUPPORT não pode criar outro CONTROLPLANE_SUPPORT", 403);
            }
            if (!SUPPORT_CAN_CREATE.contains(targetRole)) {
                throw new ApiException("FORBIDDEN", "CONTROLPLANE_SUPPORT não pode criar a role: " + targetRole, 403);
            }
            return;
        }

        throw new ApiException("FORBIDDEN", "Sua role não pode criar usuários de plataforma", 403);
    }

    // =========================
    // GUARDS
    // =========================

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private void assertNotBuiltInUserReadonly(ControlPlaneUser controlPlaneUser, String action) {
        if (controlPlaneUser.isBuiltInUser()) {
            throw new ApiException("SYSTEM_USER_READONLY",
                    "Usuário padrão do sistema é readonly e não pode sofrer ação: " + action, 409);
        }
    }

    private void assertReservedUsernameOnlyAllowedForBuiltInUsers(String username, boolean isBuiltInUser) {
        if (username == null) return;
        String u = normalizeUsername(username);
        if (RESERVED_USERNAMES.contains(u) && !isBuiltInUser) {
            throw new ApiException("RESERVED_USERNAME", "Username reservado do sistema: " + u, 409);
        }
    }

    private void assertOwnerOnly(String action) {
        ControlPlaneRole role = securityUtils.getCurrentControlPlaneRole();
        if (role != ControlPlaneRole.CONTROLPLANE_OWNER) {
            throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode executar: " + action, 403);
        }
    }

    private void assertNotSelfTarget(Long targetUserId, String action) {
        Long meId = securityUtils.getCurrentUserId();
        if (meId != null && targetUserId != null && meId.equals(targetUserId)) {
            throw new ApiException("FORBIDDEN", "Você não pode executar esta ação em si mesmo: " + action, 409);
        }
    }

    /**
     * NOT_DELETED = deleted=false (não necessariamente "enabled")
     */
    private ControlPlaneUser loadNotDeletedUserOr404(Long userId, Long accountId) {
        return controlPlaneUserRepository.findNotDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));
    }

    private ControlPlaneUser loadUserAnyStatusOr404(Long userId, Long accountId) {
        return controlPlaneUserRepository.findAnyByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));
    }

    // =========================
    // CREATE
    // =========================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest req) {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            if (req.username() == null || req.username().isBlank()) {
                throw new ApiException("INVALID_USERNAME", "Username é obrigatório", 400);
            }
            if (!req.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
                throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
            }

            if (req.password() == null || req.password().isBlank()) {
                throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
            }

            String username = normalizeUsername(req.username());
            assertReservedUsernameOnlyAllowedForBuiltInUsers(username, false);

            ControlPlaneRole roleEnum = req.role();
            ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
            assertCanCreateRole(creatorRole, roleEnum);

            if (req.permissions() != null && !req.permissions().isEmpty()
                    && creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode definir permissions explícitas", 403);
            }

            String email = (req.email() == null) ? null : req.email().trim();
            if (email == null || email.isBlank()) {
                throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
            }

            if (controlPlaneUserRepository.existsNotDeletedByUsernameIgnoreCase(controlPlaneAccount.getId(), username)) {
                throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe", 409);
            }

            if (controlPlaneUserRepository.existsNotDeletedByEmailIgnoreCase(controlPlaneAccount.getId(), email)) {
                throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
            }

            LinkedHashSet<ControlPlanePermission> normalizedPermissions =
                    normalizeControlPlanePermissionsStrict(req.permissions());

            ControlPlaneUser user = ControlPlaneUser.builder()
                    .name(req.name())
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(req.password()))
                    .role(roleEnum)
                    .account(controlPlaneAccount)
                    .origin(ControlPlaneUserOrigin.ADMIN)
                    .suspendedByAccount(false)
                    .suspendedByAdmin(false)
                    .permissions(normalizedPermissions)
                    .phone(req.phone())
                    .avatarUrl(req.avatarUrl())
                    .build();

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    // =========================
    // READ
    // =========================

    @Transactional(readOnly = true)
    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();
            return controlPlaneUserRepository.findNotDeletedByAccountId(controlPlaneAccount.getId())
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }
    
    @Transactional(readOnly = true)
    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();
            return controlPlaneUserRepository.findEnabledByAccountId(controlPlaneAccount.getId())
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    @Transactional(readOnly = true)
    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();
            ControlPlaneUser u = controlPlaneUserRepository.findEnabledByIdAndAccountId(userId, controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_ENABLED", "Usuário não encontrado ou não habilitado", 404));
            return mapToResponse(u);
        });
    }


    @Transactional(readOnly = true)
    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();
            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            return mapToResponse(user);
        });
    }

    // =========================
    // UPDATE
    // =========================

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(Long userId, ControlPlaneUserUpdateRequest req) {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            assertNotBuiltInUserReadonly(user, "UPDATE_USER");

            if (req.name() != null && !req.name().isBlank()) {
                user.setName(req.name().trim());
            }

            if (req.email() != null) {
                String newEmail = req.email().trim();

                if (newEmail.isBlank()) {
                    throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
                }

                if (user.getEmail() == null || !newEmail.equalsIgnoreCase(user.getEmail())) {
                    boolean existsOther = controlPlaneUserRepository
                            .existsOtherNotDeletedByEmailIgnoreCase(controlPlaneAccount.getId(), newEmail, user.getId());
                    if (existsOther) {
                        throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
                    }
                    user.setEmail(newEmail);
                }
            }

            if (req.username() != null && !req.username().isBlank()) {
                String newUsername = normalizeUsername(req.username());

                if (!newUsername.matches(ValidationPatterns.USERNAME_PATTERN)) {
                    throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
                }

                assertReservedUsernameOnlyAllowedForBuiltInUsers(newUsername, user.isBuiltInUser());

                if (!newUsername.equalsIgnoreCase(user.getUsername())) {
                    boolean existsOther = controlPlaneUserRepository
                            .existsOtherNotDeletedByUsernameIgnoreCase(controlPlaneAccount.getId(), newUsername, user.getId());
                    if (existsOther) {
                        throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe", 409);
                    }
                    user.setUsername(newUsername);
                }
            }

            if (req.role() != null) {
                assertOwnerOnly("UPDATE_ROLE");
                user.setRole(req.role());
            }

            if (req.permissions() != null) {
                assertOwnerOnly("UPDATE_PERMISSIONS");
                LinkedHashSet<ControlPlanePermission> normalized =
                        normalizeControlPlanePermissionsStrict(req.permissions());
                user.setPermissions(normalized);
            }

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId,
            ControlPlaneUserPermissionsUpdateRequest req
    ) {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());

            assertNotBuiltInUserReadonly(user, "UPDATE_PERMISSIONS");
            assertOwnerOnly("UPDATE_PERMISSIONS");

            LinkedHashSet<ControlPlanePermission> normalized =
                    normalizeControlPlanePermissionsStrict(req.permissions());

            user.setPermissions(normalized);

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    // =========================
    // STATUS / DELETE / RESTORE
    // =========================

    public void softDeleteControlPlaneUser(Long userId) {
        publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());

            assertNotBuiltInUserReadonly(user, "DELETE");
            assertOwnerOnly("DELETE");
            assertNotSelfTarget(userId, "DELETE");

            if (user.getRole() == ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("OWNER_DELETE_BLOCKED", "Não é permitido remover CONTROLPLANE_OWNER", 409);
            }

            LocalDateTime now = appClock.now();
            user.softDelete(now);
            controlPlaneUserRepository.save(user);
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            assertOwnerOnly("RESTORE");

            ControlPlaneUser user = loadUserAnyStatusOr404(userId, controlPlaneAccount.getId());

            if (!user.isDeleted()) {
                throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
            }

            assertRestoreNoNotDeletedCollision(controlPlaneAccount, user);

            user.restore();
            user.setSuspendedByAdmin(false);
            user.setSuspendedByAccount(false);

            assertReservedUsernameOnlyAllowedForBuiltInUsers(user.getUsername(), user.isBuiltInUser());

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserSuspended(Long userId, boolean suspended) {
        return publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());

            assertNotBuiltInUserReadonly(user, "UPDATE_STATUS");
            assertOwnerOnly("UPDATE_STATUS");
            assertNotSelfTarget(userId, "UPDATE_STATUS");

            user.setSuspendedByAdmin(suspended);
            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    // =========================
    // ME
    // =========================

    @Transactional(readOnly = true)
    public ControlPlaneMeResponse getMe() {
        return publicExecutor.run(() -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
            }

            Object principal = auth.getPrincipal();
            if (!(principal instanceof AuthenticatedUserContext ctx)) {
                throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
            }

            List<String> authorities = (ctx.getAuthorities() == null) ? List.of()
                    : ctx.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList();

            return new ControlPlaneMeResponse(
                    ctx.getUserId(),
                    ctx.getUsername(),
                    ctx.getEmail(),
                    ctx.getRoleAuthority(),
                    ctx.getAccountId(),
                    ctx.isMustChangePassword(),
                    authorities
            );
        });
    }

    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest req) {
        publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);
            if (req.newPassword() == null || req.newPassword().isBlank()) {
                throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);
            }
            if (!req.newPassword().equals(req.confirmPassword())) {
                throw new ApiException("PASSWORD_MISMATCH", "Senha e confirmação não conferem", 400);
            }

            Long meId = securityUtils.getCurrentUserId();
            if (meId == null) throw new ApiException("UNAUTHORIZED", "Usuário não autenticado", 401);

            // ✅ sem active: NOT_DELETED (e depois valida enabled via flags)
            ControlPlaneUser me = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(meId, controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (me.isDeleted()) throw new ApiException("USER_DELETED", "Usuário removido", 409);
            if (me.isSuspendedByAccount() || me.isSuspendedByAdmin()) {
                throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
            }

            if (!passwordEncoder.matches(req.currentPassword(), me.getPassword())) {
                throw new ApiException("INVALID_CURRENT_PASSWORD", "Senha atual inválida", 400);
            }

            if (passwordEncoder.matches(req.newPassword(), me.getPassword())) {
                throw new ApiException("PASSWORD_REUSE", "Nova senha não pode ser igual à senha atual", 400);
            }

            me.setPassword(passwordEncoder.encode(req.newPassword()));
            me.setMustChangePassword(false);
            me.setPasswordChangedAt(appClock.now());

            me.setFailedLoginAttempts(0);
            me.setLockedUntil(null);
            me.setPasswordResetToken(null);
            me.setPasswordResetExpires(null);

            controlPlaneUserRepository.save(me);
        });
    }

    public void resetControlPlaneUserPassword(Long targetUserId, ControlPlaneUserPasswordResetRequest req) {
        publicExecutor.run(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);
            if (req.newPassword() == null || req.newPassword().isBlank()) {
                throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);
            }
            if (!req.newPassword().equals(req.confirmPassword())) {
                throw new ApiException("PASSWORD_MISMATCH", "Senha e confirmação não conferem", 400);
            }

            assertOwnerOnly("RESET_PASSWORD");
            assertNotSelfTarget(targetUserId, "RESET_PASSWORD");

            // ✅ sem active
            ControlPlaneUser superAdmin = controlPlaneUserRepository.findNotDeletedSuperAdmin(controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException(
                            "SUPERADMIN_NOT_FOUND",
                            "Superadmin não encontrado. Rode a seed V5__insert_controlplane_users.sql",
                            500
                    ));

            if (superAdmin.getId().equals(targetUserId)) {
                throw new ApiException("SUPERADMIN_CANNOT_RESET_SELF", "Superadmin não pode resetar a própria senha", 409);
            }

            // ✅ BUG FIX: era findNotDeletedByAccountId(userId, accountId) -> método errado
            ControlPlaneUser target = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(targetUserId, controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (target.getRole() == ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("OWNER_PASSWORD_RESET_BLOCKED",
                        "Não é permitido resetar senha de CONTROLPLANE_OWNER", 409);
            }

            target.setPassword(passwordEncoder.encode(req.newPassword()));
            target.setMustChangePassword(true);
            target.setPasswordChangedAt(appClock.now());

            target.setFailedLoginAttempts(0);
            target.setLockedUntil(null);
            target.setPasswordResetToken(null);
            target.setPasswordResetExpires(null);

            controlPlaneUserRepository.save(target);
        });
    }

    // =========================
    // HELPERS
    // =========================

    private void assertRestoreNoNotDeletedCollision(Account account, ControlPlaneUser deletedUser) {
        if (deletedUser.getUsername() != null && !deletedUser.getUsername().isBlank()) {
            boolean existsOther = controlPlaneUserRepository.existsOtherNotDeletedByUsernameIgnoreCase(
                    account.getId(),
                    deletedUser.getUsername(),
                    deletedUser.getId()
            );
            if (existsOther) {
                throw new ApiException("USERNAME_ALREADY_EXISTS",
                        "Não é possível restaurar: username já está em uso", 409);
            }
        }

        if (deletedUser.getEmail() != null && !deletedUser.getEmail().isBlank()) {
            boolean existsOther = controlPlaneUserRepository.existsOtherNotDeletedByEmailIgnoreCase(
                    account.getId(),
                    deletedUser.getEmail(),
                    deletedUser.getId()
            );
            if (existsOther) {
                throw new ApiException("EMAIL_ALREADY_EXISTS",
                        "Não é possível restaurar: email já está em uso", 409);
            }
        }
    }

    private LinkedHashSet<ControlPlanePermission> normalizeControlPlanePermissionsStrict(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) return new LinkedHashSet<>();

        // ✅ STRICT: exige CP_
        LinkedHashSet<String> normalized = PermissionScopeValidator.normalizeControlPlaneStrict(raw);

        LinkedHashSet<ControlPlanePermission> out = new LinkedHashSet<>();
        for (String s : normalized) {
            if (s == null || s.isBlank()) continue;
            try {
                out.add(ControlPlanePermission.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new ApiException("INVALID_PERMISSION", "Permissão inválida: " + s, 400);
            }
        }
        return out;
    }

    private ControlPlaneUserDetailsResponse mapToResponse(ControlPlaneUser user) {
        return new ControlPlaneUserDetailsResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAccount().getId(),
                user.getPermissions() == null
                        ? List.of()
                        : user.getPermissions().stream().map(Enum::name).sorted().toList()
        );
    }
}
