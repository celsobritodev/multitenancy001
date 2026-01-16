package brito.com.multitenancy001.controlplane.application.user;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // ✅ usernames reservados do sistema (nunca pode criar outro com esses nomes)
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "superadmin", "billing", "support", "operator"
    );

    private Account getControlPlaneAccount() {
        TenantContext.clear(); // PUBLIC
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

    private static final Set<ControlPlaneRole> SUPPORT_CAN_CREATE = EnumSet.of(
            ControlPlaneRole.CONTROLPLANE_OPERATOR
    );

    private void assertCanCreateRole(ControlPlaneRole creatorRole, ControlPlaneRole targetRole) {
        if (creatorRole == null) {
            throw new ApiException("FORBIDDEN", "Role do criador não encontrada", 403);
        }
        if (targetRole == null) {
            throw new ApiException("INVALID_ROLE", "Role alvo é obrigatória", 400);
        }

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
    // ✅ GUARDS (regras novas)
    // =========================

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private void assertUsernameNotReserved(String username) {
        if (username == null) return;
        String u = normalizeUsername(username);
        if (RESERVED_USERNAMES.contains(u)) {
            throw new ApiException("RESERVED_USERNAME", "Username reservado do sistema: " + u, 409);
        }
    }

    private void assertNotSystemUserReadonly(ControlPlaneUser user, String action) {
        if (user.isSystemUser()) {
            throw new ApiException("SYSTEM_USER_READONLY",
                    "Usuário padrão do sistema é readonly e não pode sofrer ação: " + action,
                    409);
        }
    }
    
    private void assertReservedUsernameOnlyAllowedForSystemUsers(String username, boolean isSystemUser) {
        if (username == null) return;
        String u = normalizeUsername(username);
        if (RESERVED_USERNAMES.contains(u) && !isSystemUser) {
            throw new ApiException("RESERVED_USERNAME", "Username reservado do sistema: " + u, 409);
        }
    }


    private ControlPlaneUser loadActiveUserOr404(Long userId, Long accountId) {
        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404);
        }
        return user;
    }

    // =========================
    // CREATE
    // =========================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest req) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        if (req.username() == null || req.username().isBlank()) {
            throw new ApiException("INVALID_USERNAME", "Username é obrigatório", 400);
        }
        if (!req.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
        }

        String username = normalizeUsername(req.username());

        // ✅ bloqueia username reservado (superadmin/billing/support/operator)
        assertReservedUsernameOnlyAllowedForSystemUsers(username,false);

        ControlPlaneRole roleEnum = req.role();

        ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
        assertCanCreateRole(creatorRole, roleEnum);

        if (req.permissions() != null && !req.permissions().isEmpty()
                && creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
            throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode definir permissions explícitas", 403);
        }

        if (controlPlaneUserRepository.existsByUsernameAndAccountId(username, controlPlaneAccount.getId())) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe", 409);
        }
        if (controlPlaneUserRepository.existsByEmailAndAccountId(req.email(), controlPlaneAccount.getId())) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
        }

        LinkedHashSet<String> normalizedPermissions;
        try {
            normalizedPermissions = PermissionScopeValidator.normalizeControlPlane(req.permissions());
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_PERMISSION", e.getMessage(), 400);
        }

        ControlPlaneUser user = ControlPlaneUser.builder()
                .name(req.name())
                .username(username)
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .role(roleEnum)
                .account(controlPlaneAccount)
                .systemUser(false) // ✅ usuários criados pela API nunca são system users
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .permissions(normalizedPermissions)
                .build();

        return mapToResponse(controlPlaneUserRepository.save(user));
    }

    // =========================
    // READ
    // =========================

    @Transactional(readOnly = true)
    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();
        return controlPlaneUserRepository.findByAccountId(controlPlaneAccount.getId()).stream()
                .filter(u -> !u.isDeleted())
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();
        ControlPlaneUser user = loadActiveUserOr404(userId, controlPlaneAccount.getId());
        return mapToResponse(user);
    }

    // =========================
    // UPDATE (novo)
    // =========================

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(Long userId, ControlPlaneUserUpdateRequest req) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = loadActiveUserOr404(userId, controlPlaneAccount.getId());

        // ✅ system user é readonly: não pode renomear / trocar email / role / permissions / etc.
        assertNotSystemUserReadonly(user, "UPDATE_USER");

        // name
        if (req.name() != null && !req.name().isBlank()) {
            user.setName(req.name().trim());
        }

        // email (se veio)
        if (req.email() != null && !req.email().isBlank()) {
            String newEmail = req.email().trim();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                if (controlPlaneUserRepository.existsByEmailAndAccountId(newEmail, controlPlaneAccount.getId())) {
                    throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
                }
                user.setEmail(newEmail);
            }
        }

        // username (rename)
        if (req.username() != null && !req.username().isBlank()) {
            String newUsername = normalizeUsername(req.username());

            if (!newUsername.matches(ValidationPatterns.USERNAME_PATTERN)) {
                throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
            }

         // ✅ regra oficial: reservado só pode existir se systemUser=true (aqui é false pois você já bloqueou system users no começo)
            assertReservedUsernameOnlyAllowedForSystemUsers(newUsername, false);


            if (!newUsername.equals(user.getUsername())) {
                // impede colisão com outro ativo
                boolean existsOther = controlPlaneUserRepository.existsOtherActiveByUsername(
                        controlPlaneAccount.getId(), newUsername, user.getId()
                );
                if (existsOther) {
                    throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe", 409);
                }

                user.setUsername(newUsername);
            }
        }

        // role (opcional)
        if (req.role() != null) {
            // aqui você pode aplicar policy (quem pode mudar role de quem).
            // por enquanto: só OWNER pode alterar role
            ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
            if (creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode alterar role", 403);
            }
            user.setRole(req.role());
        }

        // permissions (opcional)
        if (req.permissions() != null) {
            ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
            if (creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
                throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode definir permissions explícitas", 403);
            }

            LinkedHashSet<String> normalized;
            try {
                normalized = PermissionScopeValidator.normalizeControlPlane(req.permissions());
            } catch (IllegalArgumentException e) {
                throw new ApiException("INVALID_PERMISSION", e.getMessage(), 400);
            }
            user.setPermissions(normalized);
        }

        return mapToResponse(controlPlaneUserRepository.save(user));
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId,
            ControlPlaneUserPermissionsUpdateRequest req
    ) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = loadActiveUserOr404(userId, controlPlaneAccount.getId());

        // ✅ system user não pode ter permissions alteradas
        assertNotSystemUserReadonly(user, "UPDATE_PERMISSIONS");

        ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
        if (creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
            throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode alterar permissions", 403);
        }

        LinkedHashSet<String> normalized;
        try {
            normalized = PermissionScopeValidator.normalizeControlPlane(req.permissions());
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_PERMISSION", e.getMessage(), 400);
        }

        user.setPermissions(normalized);
        return mapToResponse(controlPlaneUserRepository.save(user));
    }

    // =========================
    // STATUS / DELETE / RESTORE
    // =========================

    public ControlPlaneUserDetailsResponse updateControlPlaneUserStatus(Long userId, boolean active) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = loadActiveUserOr404(userId, controlPlaneAccount.getId());

        // ✅ system user: você até pode permitir SUSPENDER/ATIVAR (se quiser)
        // se quiser bloquear também, descomente:
        // assertNotSystemUserReadonly(user, "UPDATE_STATUS");

        user.setSuspendedByAdmin(!active);
        return mapToResponse(controlPlaneUserRepository.save(user));
    }

    public void softDeleteControlPlaneUser(Long userId) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = loadActiveUserOr404(userId, controlPlaneAccount.getId());

        // ✅ bloqueia delete de system user
        assertNotSystemUserReadonly(user, "DELETE");

        LocalDateTime now = appClock.now();
        long suffix = appClock.epochMillis();

        try {
            user.softDelete(now, suffix);
        } catch (IllegalStateException e) {
            if ("SYSTEM_USER_READONLY".equals(e.getMessage())) {
                throw new ApiException("SYSTEM_USER_READONLY", "Usuário padrão do sistema não pode ser removido", 409);
            }
            throw e;
        }

        controlPlaneUserRepository.save(user);
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (!user.isDeleted()) {
            throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
        }

        // ✅ Se quiser impedir “restaurar” system users deletados (em tese não acontece):
        // if (user.isSystemUser()) throw new ApiException(...)

        user.restore();

        // ✅ regra extra: não permitir restaurar como username reservado se alguém tentou burlar no banco
        assertUsernameNotReserved(user.getUsername());

        return mapToResponse(controlPlaneUserRepository.save(user));
    }
    
    public void resetControlPlaneUserPassword(Long targetUserId,
            brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPasswordResetRequest req) {

        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        // 1) valida request
        if (req == null) {
            throw new ApiException("INVALID_REQUEST", "Request inválido", 400);
        }
        if (req.newPassword() == null || req.newPassword().isBlank()) {
            throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);
        }
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new ApiException("PASSWORD_MISMATCH", "Senha e confirmação não conferem", 400);
        }

        // 2) quem está chamando? deve ser SUPERADMIN (OWNER)
        ControlPlaneRole actorRole = securityUtils.getCurrentControlPlaneRole();
        if (actorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
            throw new ApiException("FORBIDDEN", "Apenas CONTROLPLANE_OWNER pode resetar senha", 403);
        }

        // 3) pega superadmin "real" do banco (não só pelo token)
        ControlPlaneUser superAdmin = controlPlaneUserRepository.findActiveSuperAdmin()
                .orElseThrow(() -> new ApiException(
                        "SUPERADMIN_NOT_FOUND",
                        "Superadmin não encontrado. Rode a seed V5__insert_controlplane_users.sql",
                        500
                ));

        // 4) regra: superadmin NÃO pode resetar a própria senha (se perder, não recupera)
        if (superAdmin.getId().equals(targetUserId)) {
            throw new ApiException("SUPERADMIN_CANNOT_RESET_SELF",
                    "Superadmin não pode resetar a própria senha", 409);
        }

        // 5) carrega alvo
        ControlPlaneUser target = controlPlaneUserRepository
                .findActiveByIdAndAccountId(targetUserId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        // 6) regra: ninguém reseta senha de OWNER (garante não recuperar superadmin / nem outro owner)
        if (target.getRole() == ControlPlaneRole.CONTROLPLANE_OWNER) {
            throw new ApiException("OWNER_PASSWORD_RESET_BLOCKED",
                    "Não é permitido resetar senha de CONTROLPLANE_OWNER", 409);
        }

        // 7) (opcional, mas recomendado) permite reset mesmo se system user=true (billing/support/operator)
        // - mas ainda assim você NÃO deixa alterar mais nada deles, só senha.

        // 8) executa reset
        target.setPassword(passwordEncoder.encode(req.newPassword()));
        target.setMustChangePassword(true); // força troca no próximo login (você já usa este campo)
        target.setPasswordChangedAt(appClock.now());

        // reset de lock e tentativas falhas (boa prática)
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        target.setPasswordResetToken(null);
        target.setPasswordResetExpires(null);

        controlPlaneUserRepository.save(target);
    }

    public void changeMyPassword(brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneChangeMyPasswordRequest req) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        if (req == null) {
            throw new ApiException("INVALID_REQUEST", "Request inválido", 400);
        }
        if (req.newPassword() == null || req.newPassword().isBlank()) {
            throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);
        }
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new ApiException("PASSWORD_MISMATCH", "Senha e confirmação não conferem", 400);
        }

        // ✅ pega usuário autenticado
        Long meId = securityUtils.getCurrentUserId(); // se você não tiver, veja o helper abaixo
        if (meId == null) {
            throw new ApiException("UNAUTHORIZED", "Usuário não autenticado", 401);
        }

        ControlPlaneUser me = controlPlaneUserRepository
                .findActiveByIdAndAccountId(meId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        // bloqueios básicos
        if (me.isDeleted()) {
            throw new ApiException("USER_DELETED", "Usuário removido", 409);
        }
        if (me.isSuspendedByAccount() || me.isSuspendedByAdmin()) {
            throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
        }

        // ✅ valida senha atual
        if (!passwordEncoder.matches(req.currentPassword(), me.getPassword())) {
            throw new ApiException("INVALID_CURRENT_PASSWORD", "Senha atual inválida", 400);
        }

        // ✅ evita trocar pela mesma
        if (passwordEncoder.matches(req.newPassword(), me.getPassword())) {
            throw new ApiException("PASSWORD_REUSE", "Nova senha não pode ser igual à senha atual", 400);
        }

        // ✅ aplica mudança
        me.setPassword(passwordEncoder.encode(req.newPassword()));
        me.setMustChangePassword(false);
        me.setPasswordChangedAt(appClock.now());

        // “higiene” de segurança
        me.setFailedLoginAttempts(0);
        me.setLockedUntil(null);
        me.setPasswordResetToken(null);
        me.setPasswordResetExpires(null);

        controlPlaneUserRepository.save(me);
    }
    
    
    @Transactional(readOnly = true)
    public ControlPlaneMeResponse getMe() {
        TenantContext.clear();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof AuthenticatedUserContext ctx)) {
            throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
        }

        List<String> authorities = (ctx.getAuthorities() == null)
                ? List.of()
                : ctx.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .sorted()
                    .toList();

        return new ControlPlaneMeResponse(
                ctx.getUserId(),
                ctx.getUsername(),
                ctx.getEmail(),
                ctx.getRoleAuthority(),
                ctx.getAccountId(),
                ctx.isMustChangePassword(),
                authorities
        );
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
                        : user.getPermissions().stream().sorted().toList()
        );
    }
}
