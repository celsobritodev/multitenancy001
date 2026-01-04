package brito.com.multitenancy001.services;

import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.repositories.publicdb.AccountRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import brito.com.multitenancy001.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantUserService {

    private final TenantUserTxService tenantUserTxService; // ✅ novo
    private final AccountRepository accountRepository; // PUBLIC
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtils securityUtils;

    // ===== helpers =====
    private <T> T runInTenant(String schema, java.util.concurrent.Callable<T> action) {
        TenantContext.bindTenant(schema);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    private void runInTenant(String schema, Runnable action) {
        TenantContext.bindTenant(schema);
        try {
            action.run();
        } finally {
            TenantContext.unbindTenant();
        }
    }

    public UserResponse createTenantUser(UserCreateRequest request) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        validateCreateRequest(request);

        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();

        return runInTenant(schema, () -> toUserResponse(
                tenantUserTxService.createTenantUser(
                        accountId,
                        request.name().trim(),
                        username,
                        email,
                        request.password(),
                        request.role(),
                        request.phone(),
                        request.avatarUrl(),
                        request.permissions()
                )
        ));
    }

    public List<UserResponse> listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> tenantUserTxService.listUsers(accountId).stream().map(this::toUserResponse).toList());
    }

    public List<UserResponse> listActiveTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> tenantUserTxService.listActiveUsers(accountId).stream().map(this::toUserResponse).toList());
    }

    public UserResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> toUserResponse(tenantUserTxService.getUser(userId, accountId)));
    }

    public UserResponse updateTenantUserStatus(Long userId, boolean active) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> toUserResponse(tenantUserTxService.updateStatus(userId, accountId, active)));
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        runInTenant(schema, () -> tenantUserTxService.softDelete(userId, accountId));
    }

    public UserResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> toUserResponse(tenantUserTxService.restore(userId, accountId)));
    }

    public UserResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> toUserResponse(tenantUserTxService.resetPassword(userId, accountId, newPassword)));
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        runInTenant(schema, () -> tenantUserTxService.hardDelete(userId, accountId));
    }

    // ===== PASSWORD RESET (PUBLIC + TENANT) =====
    public String generatePasswordResetToken(String slug, String email) {
        if (!StringUtils.hasText(slug)) throw new ApiException("INVALID_SLUG", "Slug é obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);

        // PUBLIC (sem @Transactional tenant)
        TenantContext.unbindTenant();
        TenantAccount account = accountRepository.findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
        if (!account.isActive()) throw new ApiException("ACCOUNT_INACTIVE", "Conta inativa", 403);

        String schema = account.getSchemaName();
        String normalizedEmail = email.trim().toLowerCase();

        return runInTenant(schema, () -> {
//            TenantUser user = tx // aqui você pode criar um método tx.getByEmail(...)
//                    .listUsers(account.getId()).stream()
//                    .filter(u -> normalizedEmail.equals(u.getEmail()) && !u.isDeleted())
//                    .findFirst()
//                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));
            TenantUser user = tenantUserTxService.getByEmailActive(account.getId(), normalizedEmail);

            if (!user.isActive() || user.isDeleted()) throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);

            String token = jwtTokenProvider.generatePasswordResetToken(
                    user.getUsername(),
                    schema,
                    account.getId()
            );

            // Atualizar token precisa ser TX TENANT também — ideal: criar método tx.setResetToken(...)
            user.setPasswordResetToken(token);
            user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));

            tenantUserTxService.save(user);
            return token;
        });
    }
    
    
    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException("INVALID_TOKEN", "Token é obrigatório", 400);
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
        }

        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isPasswordResetToken(token)) {
            throw new ApiException("INVALID_TOKEN", "Token inválido", 400);
        }

        String schema = jwtTokenProvider.getContextFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);

        if (!StringUtils.hasText(schema) || accountId == null || !StringUtils.hasText(username)) {
            throw new ApiException("INVALID_TOKEN", "Token incompleto", 400);
        }

        // ✅ bind antes da TX (por isso usamos runInTenant)
        runInTenant(schema, () -> {
            tenantUserTxService.resetPasswordWithToken(accountId, username, token, newPassword);
        });
    }
 
    
    
    
    
    
    
    

    // ===== helpers locais =====
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

    private void validateCreateRequest(UserCreateRequest r) { /* mantém seu método */ }
}
