package brito.com.multitenancy001.tenant.application;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
import brito.com.multitenancy001.tenant.model.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class TenantUserTxService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================
    // CREATE
    // =========================
    public TenantUser createTenantUser(
            Long accountId,
            String name,
            String username,
            String email,
            String rawPassword,
            String role,
            String phone,
            String avatarUrl,
            List<String> permissions
    ) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);

        if (!StringUtils.hasText(name)) throw new ApiException("INVALID_NAME", "Nome obrigatório", 400);
        if (!StringUtils.hasText(username)) throw new ApiException("INVALID_USERNAME", "Username obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_EMAIL", "Email obrigatório", 400);

        if (!StringUtils.hasText(rawPassword) || !rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        String u = username.trim().toLowerCase();
        String e = email.trim().toLowerCase();

        if (tenantUserRepository.existsByUsernameAndAccountId(u, accountId)) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe nesta conta", 409);
        }
        if (tenantUserRepository.existsByEmailAndAccountId(e, accountId)) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe nesta conta", 409);
        }

        TenantRole parsedRole = parseTenantRole(role);
        
        TenantUser user = TenantUser.builder()
                .accountId(accountId)
                .name(name.trim())
                .username(u)
                .email(e)
                .password(passwordEncoder.encode(rawPassword))
                .role(parsedRole)
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .phone(phone)
                .avatarUrl(avatarUrl)
                .timezone("America/Sao_Paulo")
                .locale("pt_BR")
                .createdAt(LocalDateTime.now())
                .build();

        // se quiser respeitar permissions do request (opcional)
        if (permissions != null && !permissions.isEmpty()) {
            user.setPermissions(permissions);
        }

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
        // CORREÇÃO: Use o novo método customizado
        return tenantUserRepository.findActiveUsersByAccount(accountId);
    }

    @Transactional(readOnly = true)
    public TenantUser getUser(Long userId, Long accountId) {
        return tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    // usado em login/validações (sem deleted=true)
    @Transactional(readOnly = true)
    public TenantUser getByUsername(Long accountId, String username) {
        return tenantUserRepository.findByUsernameAndAccountIdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    // ✅ isto substitui seu "getByEmailActive"
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

        // ✅ ação do admin: suspende/reativa manualmente
        user.setSuspendedByAdmin(!active);

        // ✅ NÃO mexe em suspendedByAccount aqui (quem manda é o status da conta)
        user.setUpdatedAt(LocalDateTime.now());
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

        user.softDelete();
        user.setUpdatedAt(LocalDateTime.now());
        tenantUserRepository.save(user);
    }

    public TenantUser restore(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (!user.isDeleted()) throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);

        user.restore();
        user.setUpdatedAt(LocalDateTime.now());
        return tenantUserRepository.save(user);
    }

    public void hardDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
        tenantUserRepository.delete(user);
    }

    // =========================
    // RESET PASSWORD (ADMIN / USERID)
    // =========================
    public TenantUser resetPassword(Long userId, Long accountId, String newPassword) {
        if (!StringUtils.hasText(newPassword) || !newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "Senha fraca / inválida", 400);
        }

        TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now());

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
        if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new ApiException("TOKEN_EXPIRED", "Token expirado", 400);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(false);

        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);

        user.setUpdatedAt(LocalDateTime.now());
        tenantUserRepository.save(user);
    }

    // usado no generatePasswordResetToken
    public TenantUser save(TenantUser user) {
        user.setUpdatedAt(LocalDateTime.now());
        return tenantUserRepository.save(user);
    }

    private TenantRole parseTenantRole(String role) {
        if (!StringUtils.hasText(role)) throw new ApiException("INVALID_ROLE", "Role obrigatória", 400);

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