package brito.com.multitenancy001.services;

import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.tenant.TenantUserRepository;
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
public class TenantUserTxService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUser createTenantUser(Long accountId, String name, String username, String email,
                                       String rawPassword, String role, String phone, String avatarUrl,
                                       List<String> permissions) {

        if (tenantUserRepository.existsByUsernameAndAccountId(username, accountId)) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe nesta conta", 409);
        }
        if (tenantUserRepository.existsByEmailAndAccountId(email, accountId)) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe nesta conta", 409);
        }

        TenantRole parsedRole = parseTenantRole(role);

        TenantUser user = TenantUser.builder()
                .accountId(accountId)
                .name(name)
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(parsedRole)
                .active(true)
                .phone(phone)
                .avatarUrl(avatarUrl)
                .timezone("America/Sao_Paulo")
                .locale("pt_BR")
                .createdAt(LocalDateTime.now())
                .build();

        if (permissions != null && !permissions.isEmpty()) {
            user.setPermissions(permissions);
        }

        return tenantUserRepository.save(user);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<TenantUser> listUsers(Long accountId) {
        return tenantUserRepository.findByAccountIdAndDeletedFalse(accountId);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<TenantUser> listActiveUsers(Long accountId) {
        return tenantUserRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public TenantUser getUser(Long userId, Long accountId) {
        return tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUser updateStatus(Long userId, Long accountId, boolean active) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_DELETED", "Usuário está deletado", 409);

        user.setActive(active);
        user.setUpdatedAt(LocalDateTime.now());
        return tenantUserRepository.save(user);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void softDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (user.isDeleted()) throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);

        user.softDelete();
        user.setUpdatedAt(LocalDateTime.now());
        tenantUserRepository.save(user);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUser restore(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

        if (!user.isDeleted()) throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);

        user.restore();
        user.setUpdatedAt(LocalDateTime.now());
        return tenantUserRepository.save(user);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
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

    @Transactional(transactionManager = "tenantTransactionManager")
    public void hardDelete(Long userId, Long accountId) {
        TenantUser user = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
        tenantUserRepository.delete(user);
    }
    
    
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public TenantUser getByEmailActive(Long accountId, String email) {
        return tenantUserRepository.findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUser save(TenantUser user) {
        return tenantUserRepository.save(user);
    }
    
    
    @Transactional(transactionManager = "tenantTransactionManager")
    public void resetPasswordWithToken(Long accountId, String username, String token, String newPassword) {
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
 
    

    

    private TenantRole parseTenantRole(String role) {
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
