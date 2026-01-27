package brito.com.multitenancy001.controlplane.application.user;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUserOrigin;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;
    private final SecurityUtils securityUtils;
    private final PublicUnitOfWork publicUnitOfWork;

    private Account getControlPlaneAccount() {
        return accountRepository.findBySlugAndDeletedFalse("controlplane")
                .orElseThrow(() -> new ApiException(
                        "CONTROLPLANE_ACCOUNT_NOT_FOUND",
                        "Conta controlplane não encontrada. Rode a migration V4__insert_controlplane_account.sql",
                        500
                ));
    }

    private static final Set<ControlPlaneRole> OWNER_CAN_CREATE = EnumSet.of(
            ControlPlaneRole.CONTROLPLANE_SUPPORT,
            ControlPlaneRole.CONTROLPLANE_OPERATOR,
            ControlPlaneRole.CONTROLPLANE_BILLING_MANAGER
    );

    private static final Set<ControlPlaneRole> SUPPORT_CAN_CREATE =
            EnumSet.of(ControlPlaneRole.CONTROLPLANE_OPERATOR);

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

    /**
     * Trava somente os 4 emails reservados (readonly total, exceto senha).
     */
    private void assertReservedBuiltInReadonly(ControlPlaneUser user, String action) {
        if (user == null) return;

        if (ControlPlaneBuiltInUsers.isReservedEmail(user.getEmail())) {
            throw new ApiException(
                    "BUILTIN_USER_READONLY",
                    "Usuário administrativo reservado é readonly (exceto senha) e não pode sofrer ação: " + action,
                    409
            );
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
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);

            if (req.password() == null || req.password().isBlank()) {
                throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
            }

            ControlPlaneRole roleEnum = req.role();
            ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
            assertCanCreateRole(creatorRole, roleEnum);

            if (req.permissions() != null && !req.permissions().isEmpty()
                    && creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode definir permissions explícitas", 403);
            }

            String email = (req.email() == null) ? null : req.email().trim().toLowerCase(Locale.ROOT);
            if (email == null || email.isBlank()) {
                throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
            }

            if (ControlPlaneBuiltInUsers.isReservedEmail(email)) {
                throw new ApiException("RESERVED_EMAIL", "Este email é reservado para o sistema", 409);
            }

            if (controlPlaneUserRepository.existsNotDeletedByEmailIgnoreCase(controlPlaneAccount.getId(), email)) {
                throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
            }

            LinkedHashSet<ControlPlanePermission> normalizedPermissions =
                    normalizeControlPlanePermissionsStrict(req.permissions());

            ControlPlaneUser user = ControlPlaneUser.builder()
                    .name(req.name())
                    .email(email)
                    .password(passwordEncoder.encode(req.password()))
                    .role(roleEnum)
                    .account(controlPlaneAccount)
                    .origin(ControlPlaneUserOrigin.ADMIN)
                    .permissions(normalizedPermissions)
                    .mustChangePassword(true)
                    .passwordChangedAt(appClock.now())
                    .build();

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    // =========================
    // LIST / GET
    // =========================

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            List<ControlPlaneUser> users =
                    controlPlaneUserRepository.findNotDeletedByAccountId(controlPlaneAccount.getId());

            return users.stream().map(this::mapToResponse).toList();
        });
    }

    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();
            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            return mapToResponse(user);
        });
    }

    // =========================
    // UPDATE
    // =========================

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(
            Long userId,
            ControlPlaneUserUpdateRequest req
    ) {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            assertOwnerOnly("UPDATE");

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            assertReservedBuiltInReadonly(user, "UPDATE");
            assertNotSelfTarget(userId, "UPDATE");

            if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);

            if (req.name() != null) {
                user.setName(req.name());
            }

            if (req.email() != null && !req.email().isBlank()) {
                String email = req.email().trim().toLowerCase(Locale.ROOT);

                if (ControlPlaneBuiltInUsers.isReservedEmail(email)) {
                    throw new ApiException("RESERVED_EMAIL", "Este email é reservado para o sistema", 409);
                }

                boolean existsOther = controlPlaneUserRepository.existsOtherNotDeletedByEmailIgnoreCase(
                        controlPlaneAccount.getId(), email, user.getId()
                );
                if (existsOther) throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);

                user.setEmail(email);
            }

            if (req.role() != null) {
                ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
                assertCanCreateRole(creatorRole, req.role());
                user.setRole(req.role());
            }

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId, ControlPlaneUserPermissionsUpdateRequest req
    ) {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            assertOwnerOnly("UPDATE_PERMISSIONS");

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            assertReservedBuiltInReadonly(user, "UPDATE_PERMISSIONS");

            LinkedHashSet<ControlPlanePermission> normalizedPermissions =
                    normalizeControlPlanePermissionsStrict(req.permissions());

            PermissionScopeValidator.assertNoTenantPermissionLeak(
                    normalizedPermissions.stream().map(Enum::name).toList()
            );

            user.setPermissions(normalizedPermissions);

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    // =========================
    // SOFT DELETE / RESTORE / SUSPEND
    // =========================

    public void softDeleteControlPlaneUser(Long userId) {
        publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            assertOwnerOnly("SOFT_DELETE");
            assertNotSelfTarget(userId, "SOFT_DELETE");

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            assertReservedBuiltInReadonly(user, "SOFT_DELETE");

            user.softDelete(appClock.now());
            controlPlaneUserRepository.save(user);
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            assertOwnerOnly("RESTORE");

            ControlPlaneUser user = loadUserAnyStatusOr404(userId, controlPlaneAccount.getId());
            assertReservedBuiltInReadonly(user, "RESTORE");

            if (!user.isDeleted()) {
                throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
            }

            assertRestoreNoNotDeletedCollision(controlPlaneAccount, user);

            user.restore();
            user.setSuspendedByAdmin(false);
            user.setSuspendedByAccount(false);

            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserSuspended(Long userId, boolean suspended) {
        return publicUnitOfWork.tx(() -> {
            Account controlPlaneAccount = getControlPlaneAccount();

            assertOwnerOnly("UPDATE_STATUS");
            assertNotSelfTarget(userId, "UPDATE_STATUS");

            ControlPlaneUser user = loadNotDeletedUserOr404(userId, controlPlaneAccount.getId());
            assertReservedBuiltInReadonly(user, "UPDATE_STATUS");

            user.setSuspendedByAdmin(suspended);
            return mapToResponse(controlPlaneUserRepository.save(user));
        });
    }

    // =========================
    // ME
    // =========================

    public ControlPlaneMeResponse getMe() {
        return publicUnitOfWork.readOnly(() -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
            }

            Object principal = auth.getPrincipal();
            if (!(principal instanceof AuthenticatedUserContext ctx)) {
                throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
            }

            return new ControlPlaneMeResponse(
                    ctx.getUserId(),
                    ctx.getAccountId(),
                    ctx.getName(),
                    ctx.getEmail(),
                    ctx.getRoleName(),
                    ctx.isSuspendedByAccount(),
                    ctx.isSuspendedByAdmin(),
                    ctx.isDeleted(),
                    ctx.isEnabled()
            );
        });
    }

    // =========================
    // PASSWORDS (permitido para reservados)
    // =========================

    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest req) {
        publicUnitOfWork.tx(() -> {
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

            me.clearSecurityLockState();
            me.clearPasswordResetToken();


            controlPlaneUserRepository.save(me);
        });
    }

    public void resetControlPlaneUserPassword(Long targetUserId, ControlPlaneUserPasswordResetRequest req) {
        publicUnitOfWork.tx(() -> {
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

            ControlPlaneUser superAdmin = controlPlaneUserRepository
                    .findNotDeletedBuiltInOwner(
                            controlPlaneAccount.getId(),
                            ControlPlaneUserOrigin.BUILT_IN,
                            ControlPlaneRole.CONTROLPLANE_OWNER
                    )
                    .orElseThrow(() -> new ApiException(
                            "BUILTIN_OWNER_NOT_FOUND",
                            "Usuário BUILT_IN (CONTROLPLANE_OWNER) não encontrado. Rode a seed.",
                            500
                    ));

            if (superAdmin.getId().equals(targetUserId)) {
                throw new ApiException("SUPERADMIN_CANNOT_RESET_SELF", "Superadmin não pode resetar a própria senha", 409);
            }

            ControlPlaneUser target = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(targetUserId, controlPlaneAccount.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            // Não permite resetar senha de OWNER (inclusive superadmin)
            if (target.getRole() == ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("OWNER_PASSWORD_RESET_BLOCKED",
                        "Não é permitido resetar senha de CONTROLPLANE_OWNER", 409);
            }

            target.setPassword(passwordEncoder.encode(req.newPassword()));
            target.setMustChangePassword(true);
            target.setPasswordChangedAt(appClock.now());

            target.clearSecurityLockState();
            target.clearPasswordResetToken();

            controlPlaneUserRepository.save(target);
        });
    }

    // =========================
    // HELPERS
    // =========================

    private void assertRestoreNoNotDeletedCollision(Account account, ControlPlaneUser deletedUser) {
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

    // =========================
    // ENABLED (not deleted + not suspended)
    // =========================

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

    private ControlPlaneUserDetailsResponse mapToResponse(ControlPlaneUser user) {
        return new ControlPlaneUserDetailsResponse(
                user.getId(),
                user.getAccount().getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
