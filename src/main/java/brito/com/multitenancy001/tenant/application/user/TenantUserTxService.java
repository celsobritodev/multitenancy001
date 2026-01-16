package brito.com.multitenancy001.tenant.application.user;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import brito.com.multitenancy001.tenant.security.TenantRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class TenantUserTxService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    private LocalDateTime now() {
        return appClock.now();
    }

    // =========================
    // CREATE
    // =========================
    public TenantUser createTenantUser(
            Long accountId,
            String name,
            String username,
            String email,
            String rawPassword,
            TenantRole roleEnum,
            String phone,
            String avatarUrl,
            LinkedHashSet<String> permissions
    ) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);

        if (!StringUtils.hasText(name)) throw new ApiException("INVALID_NAME", "Nome obrigatório", 400);
        if (!StringUtils.hasText(username)) throw new ApiException("INVALID_USERNAME", "Username obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_EMAIL", "Email obrigatório", 400);

        if (!StringUtils.hasText(rawPassword) || !rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        String usernameNew = username.trim().toLowerCase();
        String emailNew = email.trim().toLowerCase();

        if (tenantUserRepository.existsByUsernameAndAccountId(usernameNew, accountId)) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe nesta conta", 409);
        }
        if (tenantUserRepository.existsByEmailAndAccountId(emailNew, accountId)) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe nesta conta", 409);
        }

        // ✅ normaliza e valida permissions AQUI (fonte de verdade)
        final LinkedHashSet<String> normalizedPermissions;
        try {
            normalizedPermissions = PermissionScopeValidator.normalizeTenant(
                    permissions == null ? new LinkedHashSet<>() : permissions
            );
        } catch (IllegalArgumentException e1) {
            throw new ApiException("INVALID_PERMISSION", e1.getMessage(), 400);
        }

        TenantUser user = TenantUser.builder()
                .accountId(accountId)
                .name(name.trim())
                .username(usernameNew)
                .email(emailNew)
                .password(passwordEncoder.encode(rawPassword))
                .role(roleEnum)
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .phone(phone)
                .avatarUrl(avatarUrl)
                .timezone("America/Sao_Paulo")
                .locale("pt_BR")
                .build();

        // ✅ Se trouxe permissions válidas e não vazias, respeita.
        // Se veio vazio/null: deixa o @PrePersist do entity aplicar defaults por role.
        if (!normalizedPermissions.isEmpty()) {
            user.setPermissions(new LinkedHashSet<>(normalizedPermissions));
        }

        return tenantUserRepository.save(user);
    }

    public TenantUser setSuspendedByAdmin(Long userId, Long accountId, boolean suspended) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_DELETED", "Usuário está deletado", 409);

        LocalDateTime now = now();
        user.setSuspendedByAdmin(suspended);
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TenantUser getUserByUsernameOrEmail(String usernameOrEmail, Long accountId) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        }
        if (!StringUtils.hasText(usernameOrEmail)) {
            throw new ApiException("INVALID_LOGIN", "Username/Email é obrigatório", 400);
        }

        String login = usernameOrEmail.trim().toLowerCase();

        if (login.contains("@")) {
            return tenantUserRepository.findByEmailAndAccountIdAndDeletedFalse(login, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
        }

        return tenantUserRepository.findByUsernameAndAccountIdAndDeletedFalse(login, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    public TenantUser updateProfile(
            Long userId,
            Long accountId,
            String name,
            String phone,
            String locale,
            String timezone,
            LocalDateTime nowParam
    ) {
        if (userId == null) throw new ApiException("USER_REQUIRED", "UserId obrigatório", 400);
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);

        TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (StringUtils.hasText(name)) user.setName(name.trim());
        if (phone != null) user.setPhone(phone);
        if (StringUtils.hasText(locale)) user.setLocale(locale.trim());
        if (StringUtils.hasText(timezone)) user.setTimezone(timezone.trim());

        LocalDateTime now = (nowParam != null ? nowParam : now());
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
        if (updated == 0) {
            throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
        }
    }

    // =========================
    // LIST / GET
    // =========================
    @Transactional(readOnly = true)
    public List<TenantUser> listUsers(Long accountId) {
        return tenantUserRepository.findByAccountIdAndDeletedFalse(accountId);
    }

    @Transactional(readOnly = true)
    public List<TenantUser> listActiveUsers(Long accountId) {
        return tenantUserRepository.findActiveUsersByAccount(accountId);
    }

    @Transactional(readOnly = true)
    public TenantUser getUser(Long userId, Long accountId) {
        return tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    @Transactional(readOnly = true)
    public TenantUser getByUsername(Long accountId, String username) {
        return tenantUserRepository.findByUsernameAndAccountIdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    @Transactional(readOnly = true)
    public TenantUser getByEmailActive(Long accountId, String email) {
        TenantUser user = tenantUserRepository.findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
            throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
        }
        return user;
    }

    // =========================
    // UPDATE STATUS
    // =========================
    public TenantUser updateStatus(Long userId, Long accountId, boolean active) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_DELETED", "Usuário está deletado", 409);

        LocalDateTime now = now();

        user.setSuspendedByAdmin(!active);
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    // =========================
    // CONTAGEM
    // =========================
    @Transactional(readOnly = true)
    public long countActiveUsers(Long accountId) {
        return tenantUserRepository.countActiveUsersByAccount(accountId);
    }

    public void softDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);

        LocalDateTime now = now();
        user.softDelete(now, appClock.epochMillis()); // ✅ novo método clock-aware
        user.setUpdatedAt(now);

        tenantUserRepository.save(user);
    }

    public TenantUser restore(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (!user.isDeleted()) throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);

        LocalDateTime now = now();

        user.restore();
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    public void hardDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
        tenantUserRepository.delete(user);
    }

    public TenantUser resetPassword(Long userId, Long accountId, String newPassword) {
        if (!StringUtils.hasText(newPassword) || !newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        LocalDateTime now = now();

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    // =========================
    // RESET PASSWORD (TOKEN)
    // =========================
    public void resetPasswordWithToken(Long accountId, String username, String token, String newPassword) {
        if (!StringUtils.hasText(newPassword) || !newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        TenantUser user = tenantUserRepository.findByUsernameAndAccountId(username, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.getPasswordResetToken() == null || !user.getPasswordResetToken().equals(token)) {
            throw new ApiException("INVALID_TOKEN", "Token não confere", 400);
        }

        LocalDateTime now = now();

        if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(now)) {
            throw new ApiException("TOKEN_EXPIRED", "Token expirado", 400);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);

        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);

        user.setUpdatedAt(now);
        tenantUserRepository.save(user);
    }

    // usado no generatePasswordResetToken
    public TenantUser save(TenantUser user) {
        LocalDateTime now = now();
        user.setUpdatedAt(now);
        return tenantUserRepository.save(user);
    }

    public void transferTenantOwnerRole(Long accountId, Long fromUserId, Long toUserId) {
        TenantUser from = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(fromUserId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "TENANT_OWNER não encontrado", 404));

        TenantUser to = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(toUserId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "TENANT_ACCOUNT_ADMIN alvo não encontrado", 404));

        LocalDateTime now = now();

        from.setRole(TenantRole.TENANT_ADMIN);
        to.setRole(TenantRole.TENANT_OWNER);

        from.setUpdatedAt(now);
        to.setUpdatedAt(now);

        tenantUserRepository.save(from);
        tenantUserRepository.save(to);
    }
}
