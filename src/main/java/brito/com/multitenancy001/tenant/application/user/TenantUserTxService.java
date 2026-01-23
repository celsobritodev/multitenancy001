package brito.com.multitenancy001.tenant.application.user;

import brito.com.multitenancy001.infrastructure.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.domain.user.TenantUserOrigin;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
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

    // =========================================================
    // CREATE
    // =========================================================
    public TenantUser createTenantUser(
            Long accountId,
            String name,
            String username,
            String email,
            String rawPassword,
            TenantRole roleEnum,
            String phone,
            String avatarUrl,
            LinkedHashSet<String> permissions,
            TenantUserOrigin origin
    ) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);

        if (!StringUtils.hasText(name)) throw new ApiException("INVALID_NAME", "Nome obrigatório", 400);
        if (!StringUtils.hasText(username)) throw new ApiException("INVALID_USERNAME", "Username obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_EMAIL", "Email obrigatório", 400);

        if (roleEnum == null) throw new ApiException("INVALID_ROLE", "Role obrigatória", 400);

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

        TenantUserOrigin originNorm = (origin != null) ? origin : TenantUserOrigin.ADMIN;

        if (originNorm == TenantUserOrigin.BUILT_IN) {
            throw new ApiException("INVALID_ORIGIN", "Origin BUILT_IN não pode ser criado via API", 400);
        }

        final LinkedHashSet<TenantPermission> normalizedPermissions =
                normalizeTenantPermissionsStrict(permissions);

        TenantUser user = TenantUser.builder()
                .accountId(accountId)
                .origin(originNorm)
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

        // Se veio permissions válidas e não vazias, respeita.
        // Se veio vazio/null: deixa o @PrePersist do entity aplicar defaults por role.
        if (!normalizedPermissions.isEmpty()) {
            user.setPermissions(new LinkedHashSet<>(normalizedPermissions));
        }

        return tenantUserRepository.save(user);
    }

    // =========================================================
    // SUSPENSION (PADRÃO ÚNICO)
    // =========================================================

    /**
     * SUSPENSÃO via UPDATE direto no banco (mais performático).
     * Se updated==0: não existe ou foi removido (deleted=true).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "UserId obrigatório", 400);

        int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
        if (updated == 0) {
            throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
        }
    }

    /**
     * Variante que carrega entidade (útil se você precisar validar/retornar o user).
     * Mantém semântica clara: só existe para casos que precisam do objeto.
     */
    public TenantUser setSuspendedByAdminAndReturn(Long userId, Long accountId, boolean suspended) {
        if (userId == null) throw new ApiException("USER_REQUIRED", "UserId obrigatório", 400);
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);

        TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_DELETED", "Usuário está deletado", 409);

        LocalDateTime now = now();
        user.setSuspendedByAdmin(suspended);
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    // =========================================================
    // GET BY LOGIN
    // =========================================================

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
            TenantUser user = tenantUserRepository.findByEmailAndAccountId(login, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isDeleted()) {
                throw new ApiException("USER_DELETED", "Usuário está deletado", 409);
            }
            return user;
        }

        return tenantUserRepository.findByUsernameAndAccountIdAndDeletedFalse(login, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    // =========================================================
    // UPDATE EMAIL
    // =========================================================
    public TenantUser updateUserEmail(Long userId, Long accountId, String newEmail) {
        if (userId == null) throw new ApiException("USER_REQUIRED", "UserId obrigatório", 400);
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        if (!StringUtils.hasText(newEmail)) throw new ApiException("INVALID_EMAIL", "Email obrigatório", 400);

        String emailNew = newEmail.trim().toLowerCase();

        TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (tenantUserRepository.existsByEmailAndAccountIdAndIdNot(emailNew, accountId, userId)) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe nesta conta", 409);
        }

        LocalDateTime now = now();
        user.setEmail(emailNew);
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    // =========================================================
    // UPDATE PROFILE
    // =========================================================
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

    // =========================================================
    // LIST / GET
    // =========================================================
    @Transactional(readOnly = true)
    public List<TenantUser> listUsers(Long accountId) {
        return tenantUserRepository.findByAccountIdAndDeletedFalse(accountId);
    }

    /**
     * "Enabled" = usuário não deletado e não suspenso por account/admin.
     * Renomeado para evitar o termo "Active" (ambíguo).
     */
    @Transactional(readOnly = true)
    public List<TenantUser> listEnabledUsers(Long accountId) {
        return tenantUserRepository.findEnabledUsersByAccount(accountId);
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

    /**
     * Renomeado: era getByEmailActive (active virou ambíguo).
     * Enabled = não deletado e não suspenso.
     */
    @Transactional(readOnly = true)
    public TenantUser getEnabledByEmail(Long accountId, String email) {
        TenantUser user = tenantUserRepository.findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
            throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
        }
        return user;
    }

    // =========================================================
    // DELETE / RESTORE
    // =========================================================

    public void softDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);

        LocalDateTime now = now();
        user.softDelete(now, appClock.epochMillis());
        user.setUpdatedAt(now);

        tenantUserRepository.save(user);
    }

    public TenantUser restore(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (!user.isDeleted()) throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);

        LocalDateTime now = now();

        user.restore();
        user.setUpdatedAt(now);

        return tenantUserRepository.save(user);
    }

    public void hardDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
        tenantUserRepository.delete(user);
    }

    // =========================================================
    // PASSWORD
    // =========================================================

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

    public void resetPasswordWithToken(Long accountId, String username, String token, String newPassword) {
        if (!StringUtils.hasText(newPassword) || !newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        }
        if (!StringUtils.hasText(token)) {
            throw new ApiException("INVALID_TOKEN", "Token inválido", 400);
        }

        LocalDateTime now = now();

        TenantUser user = tenantUserRepository
                .findByPasswordResetTokenAndAccountId(token, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_DELETED", "Usuário está deletado", 409);
        }

        if (StringUtils.hasText(username) && !user.getUsername().equals(username)) {
            throw new ApiException("INVALID_TOKEN", "Token não confere", 400);
        }

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

    // =========================================================
    // ROLE TRANSFER
    // =========================================================
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

    // =========================================================
    // CONTAGEM / LIMITES
    // =========================================================
    @Transactional(readOnly = true)
    public long countUsersForLimit(Long accountId, UserLimitPolicy policy) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        }
        if (policy == null) {
            policy = UserLimitPolicy.SEATS_IN_USE;
        }

        return switch (policy) {
            case SEATS_IN_USE ->
                    tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);

            case ACTIVE_USERS_ONLY ->
                    tenantUserRepository.countByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId);
        };
    }

    /**
     * Política A (SEATS): usuários em uso = deleted=false
     */
    @Transactional(readOnly = true)
    public long countSeatsInUse(Long accountId) {
        return countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE);
    }

    // =========================================================
    // PERMISSIONS (STRICT)
    // =========================================================

    private LinkedHashSet<TenantPermission> normalizeTenantPermissionsStrict(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) return new LinkedHashSet<>();

        // ✅ STRICT: NÃO auto-prefixa (exige TEN_)
        LinkedHashSet<String> normalized = PermissionScopeValidator.normalizeTenantStrict(raw);

        LinkedHashSet<TenantPermission> out = new LinkedHashSet<>();
        for (String s : normalized) {
            if (s == null || s.isBlank()) continue;
            try {
                out.add(TenantPermission.valueOf(s.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException("INVALID_PERMISSION", "Permissão inválida: " + s, 400);
            }
        }
        return out;
    }
    
    @Transactional(readOnly = true)
    public TenantUser getEnabledUser(Long userId, Long accountId) {
        if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);

        return tenantUserRepository.findEnabledByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_ENABLED", "Usuário não encontrado ou não habilitado", 404));
    }

    @Transactional(readOnly = true)
    public long countEnabledUsersByAccount(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        // “Active” aqui = enabled (não deletado e não suspenso)
        return tenantUserRepository.countEnabledUsersByAccount(accountId);
    }

    
}
