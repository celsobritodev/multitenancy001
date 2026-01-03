package brito.com.multitenancy001.services;

import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.TenantUserRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import brito.com.multitenancy001.security.SecurityUtils;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager") // ✅ Especifica qual TM  
public class TenantUserService {

    private final TenantUserRepository tenantUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtils securityUtils;

    /* =========================================================
       CRUD / LIST (usado pelo UserTenantController)
       ========================================================= */

    public UserResponse createTenantUser(UserCreateRequest request) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            validateCreateRequest(request);

            String username = request.username().trim().toLowerCase();
            String email = request.email().trim().toLowerCase();

            if (tenantUserRepository.existsByUsernameAndAccountId(username, accountId)) {
                throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe nesta conta", 409);
            }
            if (tenantUserRepository.existsByEmailAndAccountId(email, accountId)) {
                throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe nesta conta", 409);
            }

            TenantRole role = parseTenantRole(request.role());

            TenantUser user = TenantUser.builder()
                    .accountId(accountId)
                    .name(request.name().trim())
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(request.password()))
                    .role(role)
                    .active(true)
                    .phone(request.phone())
                    .avatarUrl(request.avatarUrl())
                    .timezone("America/Sao_Paulo")
                    .locale("pt_BR")
                    .createdAt(LocalDateTime.now())
                    .build();

            // se vier permissions no request e você quiser respeitar:
            // (se não vier, o @PrePersist já coloca default)
            if (request.permissions() != null && !request.permissions().isEmpty()) {
                user.setPermissions(request.permissions());
            }

            TenantUser saved = tenantUserRepository.save(user);
            return toUserResponse(saved);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            return tenantUserRepository.findByAccountIdAndDeletedFalse(accountId)
                    .stream().map(this::toUserResponse).toList();
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listActiveTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            return tenantUserRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId)
                    .stream().map(this::toUserResponse).toList();
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
            return toUserResponse(user);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    public UserResponse updateTenantUserStatus(Long userId, boolean active) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isDeleted()) {
                throw new ApiException("USER_DELETED", "Usuário está deletado", 409);
            }

            user.setActive(active);
            user.setUpdatedAt(LocalDateTime.now());
            return toUserResponse(tenantUserRepository.save(user));
        } finally {
            TenantContext.unbindTenant();
        }
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isDeleted()) {
                throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);
            }

            user.softDelete();
            user.setUpdatedAt(LocalDateTime.now());
            tenantUserRepository.save(user);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    public UserResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (!user.isDeleted()) {
                throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
            }

            user.restore();
            user.setUpdatedAt(LocalDateTime.now());
            return toUserResponse(tenantUserRepository.save(user));
        } finally {
            TenantContext.unbindTenant();
        }
    }

    public UserResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        if (!StringUtils.hasText(newPassword) || !newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setMustChangePassword(false);
            user.setUpdatedAt(LocalDateTime.now());

            return toUserResponse(tenantUserRepository.save(user));
        } finally {
            TenantContext.unbindTenant();
        }
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
            tenantUserRepository.delete(user);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    /* =========================================================
       RESET PASSWORD (usado pelo TenantAuthController)
       ========================================================= */

   public String generatePasswordResetToken(String slug, String email) {
    if (!StringUtils.hasText(slug)) {
        throw new ApiException("INVALID_SLUG", "Slug é obrigatório", 400);
    }
    if (!StringUtils.hasText(email)) {
        throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
    }

    // 1) PUBLIC: resolve conta
    TenantContext.unbindTenant();
    TenantAccount account = accountRepository.findBySlugAndDeletedFalse(slug)
            .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

    if (!account.isActive()) {
        throw new ApiException("ACCOUNT_INACTIVE", "Conta inativa", 403);
    }

    // 2) TENANT: bind no schema do tenant
    String schema = account.getSchemaName();
    TenantContext.bindTenant(schema);
    try {
        String normalizedEmail = email.trim().toLowerCase();

        TenantUser user = tenantUserRepository
                .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (!user.isActive() || user.isDeleted()) {
            throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
        }

        String token = jwtTokenProvider.generatePasswordResetToken(
                user.getUsername(),
                schema,
                account.getId()
        );

        user.setPasswordResetToken(token);
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
        tenantUserRepository.save(user);

        return token;

    } finally {
        TenantContext.unbindTenant();
    }
}

    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException("INVALID_TOKEN", "Token é obrigatório", 400);
        }
        if (!StringUtils.hasText(newPassword) || !newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isPasswordResetToken(token)) {
            throw new ApiException("INVALID_TOKEN", "Token inválido", 400);
        }

        String schema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);

        if (!StringUtils.hasText(schema) || accountId == null || !StringUtils.hasText(username)) {
            throw new ApiException("INVALID_TOKEN", "Token incompleto", 400);
        }

        TenantContext.bindTenant(schema);
        try {
            TenantUser user = tenantUserRepository.findByUsernameAndAccountId(username, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getPasswordResetToken() == null || !user.getPasswordResetToken().equals(token)) {
                throw new ApiException("INVALID_TOKEN", "Token não confere", 400);
            }
            if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
                throw new ApiException("TOKEN_EXPIRED", "Token expirado", 400);
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setMustChangePassword(false);

            user.setPasswordResetToken(null);
            user.setPasswordResetExpires(null);

            tenantUserRepository.save(user);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    /* =========================================================
       HELPERS
       ========================================================= */

    private UserResponse toUserResponse(TenantUser u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getName(),
                u.getEmail(),
                u.getRole() != null ? u.getRole().name() : null,
                u.isActive(),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                u.getAccountId(),
                u.getPermissions()
        );
    }

    private void validateCreateRequest(UserCreateRequest r) {
        if (r == null) throw new ApiException("INVALID_BODY", "Body obrigatório", 400);

        if (!StringUtils.hasText(r.name())) throw new ApiException("INVALID_NAME", "Nome obrigatório", 400);
        if (!StringUtils.hasText(r.username())) throw new ApiException("INVALID_USERNAME", "Username obrigatório", 400);
        if (!r.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
        }

        if (!StringUtils.hasText(r.email())) throw new ApiException("INVALID_EMAIL", "Email obrigatório", 400);

        if (!StringUtils.hasText(r.password())) throw new ApiException("INVALID_PASSWORD", "Senha obrigatória", 400);
        if (!r.password().matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        if (!StringUtils.hasText(r.role())) throw new ApiException("INVALID_ROLE", "Role obrigatória", 400);
    }

    private TenantRole parseTenantRole(String role) {
        // ⚠️ Seu enum é: TENANT_ADMIN, MANAGER, VIEWER, USER.
        // Seu UserCreateRequest tinha regex com ADMIN|PRODUCT_MANAGER|... (incompatível).
        // Aqui mapeio ambos para você não quebrar requests antigos.
        String r = role.trim().toUpperCase();

        return switch (r) {
            case "TENANT_ADMIN", "ADMIN" -> TenantRole.TENANT_ADMIN;
            case "MANAGER", "PRODUCT_MANAGER", "SALES_MANAGER" -> TenantRole.MANAGER;
            case "VIEWER" -> TenantRole.VIEWER;
            case "USER" -> TenantRole.USER;
            default -> throw new ApiException("INVALID_ROLE", "Role inválida: " + role, 400);
        };
    }
}
