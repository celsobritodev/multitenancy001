package brito.com.multitenancy001.controlplane.application.user;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
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


    private Account getControlPlaneAccount() {
        TenantContext.clear(); // PUBLIC
        return accountRepository.findBySlugAndDeletedFalse("controlplane")
                .orElseThrow(() -> new ApiException(
                        "CONTROLPLANE_ACCOUNT_NOT_FOUND",
                        "Conta controlplane não encontrada. Rode a migration V3__insert_controlplane_account.sql",
                        500
                ));
    }
    
 // ===== Policy switches (mude para true se quiser liberar) =====
  

    private static final Set<ControlPlaneRole> OWNER_CAN_CREATE = EnumSet.of(
            ControlPlaneRole.CONTROLPLANE_SUPPORT,
            ControlPlaneRole.CONTROLPLANE_OPERATOR,
            ControlPlaneRole.CONTROLPLANE_BILLING_MANAGER
    );

    private static final Set<ControlPlaneRole> SUPPORT_CAN_CREATE = EnumSet.of(
            ControlPlaneRole.CONTROLPLANE_OPERATOR
            // (opcional) ControlPlaneRole.CONTROLPLANE_SUPPORT se ALLOW_SUPPORT_CREATE_SUPPORT=true
    );

   private void assertCanCreateRole(ControlPlaneRole creatorRole, ControlPlaneRole targetRole) {
    if (creatorRole == null) {
        throw new ApiException("FORBIDDEN", "Role do criador não encontrada", 403);
    }
    if (targetRole == null) {
        throw new ApiException("INVALID_ROLE", "Role alvo é obrigatória", 400);
    }

    // OWNER
    if (creatorRole == ControlPlaneRole.CONTROLPLANE_OWNER) {
        // ❌ aqui, por padrão, OWNER NÃO cria outro OWNER (mais seguro)
        if (targetRole == ControlPlaneRole.CONTROLPLANE_OWNER) {
            throw new ApiException("FORBIDDEN", "CONTROLPLANE_OWNER não pode criar outro CONTROLPLANE_OWNER", 403);
        }
        if (!OWNER_CAN_CREATE.contains(targetRole)) {
            throw new ApiException("FORBIDDEN", "CONTROLPLANE_OWNER não pode criar a role: " + targetRole, 403);
        }
        return;
    }

    // SUPPORT
    if (creatorRole == ControlPlaneRole.CONTROLPLANE_SUPPORT) {
        // ❌ aqui, por padrão, SUPPORT NÃO cria outro SUPPORT (mais seguro)
        if (targetRole == ControlPlaneRole.CONTROLPLANE_SUPPORT) {
            throw new ApiException("FORBIDDEN", "CONTROLPLANE_SUPPORT não pode criar outro CONTROLPLANE_SUPPORT", 403);
        }
        if (!SUPPORT_CAN_CREATE.contains(targetRole)) {
            throw new ApiException("FORBIDDEN", "CONTROLPLANE_SUPPORT não pode criar a role: " + targetRole, 403);
        }
        return;
    }

    // BILLING_MANAGER e OPERATOR: não criam ninguém
    throw new ApiException("FORBIDDEN", "Sua role não pode criar usuários de plataforma", 403);
}

    

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest req) {
        TenantContext.clear();

        Account controlPlaneAccount = getControlPlaneAccount();

        if (req.username() == null || req.username().isBlank()) {
            throw new ApiException("INVALID_USERNAME", "Username é obrigatório", 400);
        }
        if (!req.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
        }

        String username = req.username().toLowerCase().trim();

        // ✅ role já tipada
        ControlPlaneRole roleEnum = req.role();
        
     // ✅ policy: "quem pode criar quem"
        ControlPlaneRole creatorRole = securityUtils.getCurrentControlPlaneRole();
        assertCanCreateRole(creatorRole, roleEnum);

        // ✅ opcional (recomendado): só OWNER pode enviar permissions explícitas no payload
        if (req.permissions() != null && !req.permissions().isEmpty() && creatorRole != ControlPlaneRole.CONTROLPLANE_OWNER) {
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
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .permissions(normalizedPermissions)
                .build();

        return mapToResponse(controlPlaneUserRepository.save(user));
    }

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

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404);
        }

        return mapToResponse(user);
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserStatus(Long userId, boolean active) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_DELETED", "Usuário está removido", 409);
        }

        // ✅ ação manual do admin da plataforma
        user.setSuspendedByAdmin(!active);

        return mapToResponse(controlPlaneUserRepository.save(user));
    }

    public void softDeleteControlPlaneUser(Long userId) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);
        }

        LocalDateTime now = appClock.now();
        long suffix = appClock.epochMillis();

        user.softDelete(now, suffix);
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

        user.restore();
        return mapToResponse(controlPlaneUserRepository.save(user));
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
