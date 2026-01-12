package brito.com.multitenancy001.tenant.application.user;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.account.AccountResolver;
import brito.com.multitenancy001.shared.account.AccountSnapshot;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;

@Service
@RequiredArgsConstructor
public class TenantUserService {

    private final TenantUserApiMapper tenantUserApiMapper;

    private final TenantUserTxService tenantUserTxService;
    private final AccountResolver accountResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;


    // ===== helpers =====
    private <T> T runInTenant(String schema, Callable<T> action) {
        TenantContext.bind(schema);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            TenantContext.clear();
        }
    }

    private void runInTenant(String schema, Runnable action) {
        TenantContext.bind(schema);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }

    // =========================================================
    // CONTROLLER METHODS
    // =========================================================

    public void transferTenantOwner(Long toUserId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long fromUserId = securityUtils.getCurrentUserId();

        runInTenant(schema, () ->
                tenantUserTxService.transferTenantOwnerRole(accountId, fromUserId, toUserId)
        );
    }

    public TenantUserDetailsResponse createTenantUser(TenantUserCreateRequest tenantUserCreateRequest) {
    Long accountId = securityUtils.getCurrentAccountId();
    String schema = securityUtils.getCurrentSchema();

  

    String name = tenantUserCreateRequest.name().trim();
    String username = tenantUserCreateRequest.username().trim().toLowerCase();
    String email = tenantUserCreateRequest.email().trim().toLowerCase();


    final LinkedHashSet<String> perms =
            (tenantUserCreateRequest.permissions() == null || tenantUserCreateRequest.permissions().isEmpty())
                    ? null
                    : new LinkedHashSet<>(tenantUserCreateRequest.permissions());

    return runInTenant(schema, () -> {
    	TenantUser created = tenantUserTxService.createTenantUser(
    	        accountId,
    	        name,                             // já trimado
    	        username,
    	        email,
    	        tenantUserCreateRequest.password(),
    	        tenantUserCreateRequest.role(),
    	        tenantUserCreateRequest.phone(),
    	        tenantUserCreateRequest.avatarUrl(),
    	        perms
    	);


        return tenantUserApiMapper.toDetails(created);
    });
}


    public List<TenantUserSummaryResponse> listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () ->
                tenantUserTxService.listUsers(accountId)
                        .stream()
                        .map(tenantUserApiMapper::toSummary)
                        .toList()
        );
    }

    public List<TenantUserSummaryResponse> listActiveTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () ->
                tenantUserTxService.listActiveUsers(accountId)
                        .stream()
                        .map(tenantUserApiMapper::toSummary)
                        .toList()
        );
    }

    public TenantUserDetailsResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> {
            TenantUser user = tenantUserTxService.getUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public TenantUserSummaryResponse updateTenantUserStatus(Long userId, boolean active) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> {
            TenantUser updated = tenantUserTxService.updateStatus(userId, accountId, active);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        runInTenant(schema, () -> tenantUserTxService.softDelete(userId, accountId));
    }

    public TenantUserSummaryResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> {
            TenantUser restored = tenantUserTxService.restore(userId, accountId);
            return tenantUserApiMapper.toSummary(restored);
        });
    }

    public TenantUserSummaryResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return runInTenant(schema, () -> {
            TenantUser updated = tenantUserTxService.resetPassword(userId, accountId, newPassword);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        runInTenant(schema, () -> tenantUserTxService.hardDelete(userId, accountId));
    }

    // =========================================================
    // PASSWORD RESET (PUBLIC -> TENANT)
    // =========================================================

    public String generatePasswordResetToken(String slug, String usernameOrEmail) {
        if (!StringUtils.hasText(slug)) throw new ApiException("INVALID_SLUG", "Slug é obrigatório", 400);
        if (!StringUtils.hasText(usernameOrEmail)) throw new ApiException("INVALID_LOGIN", "Username/Email é obrigatório", 400);

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(slug);

        return runInTenant(account.schemaName(), () -> {
            TenantUser user = tenantUserTxService.getUserByUsernameOrEmail(usernameOrEmail, account.id());

            if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            String token = jwtTokenProvider.generatePasswordResetToken(
                    user.getUsername(),
                    account.schemaName(),
                    account.id()
            );

            user.setPasswordResetToken(token);
            user.setPasswordResetExpires(appClock.now().plusHours(1));
            tenantUserTxService.save(user);

            return token;
        });
    }

    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) throw new ApiException("INVALID_TOKEN", "Token inválido", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);

        String schema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);

        runInTenant(schema, () ->
                tenantUserTxService.resetPasswordWithToken(accountId, username, token, newPassword)
        );
    }

    // =========================================================
    // MY PROFILE (agora existe no TxService)
    // =========================================================

    public TenantUserDetailsResponse updateMyProfile(String name, String phone, String locale, String timezone) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return runInTenant(schema, () -> {
            TenantUser updated = tenantUserTxService.updateProfile(
                    userId,
                    accountId,
                    name,
                    phone,
                    locale,
                    timezone,
                    appClock.now()

            );
            return tenantUserApiMapper.toDetails(updated);
        });
    }
}
