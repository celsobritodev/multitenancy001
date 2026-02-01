package brito.com.multitenancy001.tenant.users.app;

import brito.com.multitenancy001.infrastructure.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.infrastructure.publicschema.AccountEntitlementsService;
import brito.com.multitenancy001.infrastructure.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.infrastructure.publicschema.AccountResolver;
import brito.com.multitenancy001.infrastructure.publicschema.AccountSnapshot;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.api.dto.*;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantUserService {

    private final TenantUserApiMapper tenantUserApiMapper;

    private final TenantUserTxService tenantUserTxService;
    private final AccountResolver accountResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;
    private final AccountEntitlementsGuard accountEntitlementsGuard;

    // ✅ NOVO: para buscar entitlements efetivos (somente TENANT_OWNER)
    private final AccountEntitlementsService accountEntitlementsService;

    private final TenantExecutor tenantExecutor;

    // =========================================================
    // CONTROLLER METHODS
    // =========================================================

    public void transferTenantOwner(Long toUserId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long fromUserId = securityUtils.getCurrentUserId();

        tenantExecutor.run(schema, () ->
                tenantUserTxService.transferTenantOwnerRole(accountId, fromUserId, toUserId)
        );
    }

    public TenantMeResponse getMyProfile() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.run(schema, () -> {
            TenantUser user = tenantUserTxService.getUser(userId, accountId);
            return tenantUserApiMapper.toMe(user);
        });
    }

   public TenantUserDetailsResponse createTenantUser(TenantUserCreateRequest req) {
    Long accountId = securityUtils.getCurrentAccountId();
    String schema = securityUtils.getCurrentSchema();

    if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);

    String name = (req.name() == null) ? null : req.name().trim();
    String email = (req.email() == null) ? null : req.email().trim().toLowerCase();

    final LinkedHashSet<String> perms =
            (req.permissions() == null || req.permissions().isEmpty())
                    ? null
                    : new LinkedHashSet<>(req.permissions());

    EntityOrigin origin = (req.origin() != null) ? req.origin() : EntityOrigin.ADMIN;

    if (origin == EntityOrigin.BUILT_IN) {
        throw new ApiException("INVALID_ORIGIN", "Origin BUILT_IN não pode ser criado via API", 400);
    }

    // ✅ NOVO: pega mustChangePassword do request (default false)
    Boolean mustChangePassword = (req.mustChangePassword() == null) ? Boolean.FALSE : req.mustChangePassword();

    long currentUsers = tenantExecutor.run(schema, () ->
            tenantUserTxService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE)
    );

    accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);

    return tenantExecutor.run(schema, () -> {
        TenantUser created = tenantUserTxService.createTenantUser(
                accountId,
                name,
                email,
                req.password(),
                req.role(),
                req.phone(),
                req.avatarUrl(),
                perms,
                mustChangePassword,
                origin
        );

        return tenantUserApiMapper.toDetails(created);
    });
}


    /**
     * ✅ MUDOU: agora retorna wrapper com entitlements + lista.
     * - TENANT_OWNER: lista rica + entitlements
     * - outros: lista básica (compatível) e entitlements=null
     */
    public TenantUsersListResponse listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        TenantRole currentRole = securityUtils.getCurrentTenantRole();
        boolean isOwner = currentRole != null && currentRole.isTenantOwner();

        AccountEntitlementsSnapshot entitlements = null;
        if (isOwner) {
            entitlements = accountEntitlementsService.resolveEffectiveByAccountId(accountId);
        }
        AccountEntitlementsSnapshot finalEntitlements = entitlements;

        return tenantExecutor.run(schema, () -> {
            List<TenantUser> users = tenantUserTxService.listUsers(accountId);

            List<TenantUserListItemResponse> mapped = users.stream()
                    .map(u -> isOwner
                            ? tenantUserApiMapper.toListItemRich(u)
                            : tenantUserApiMapper.toListItemBasic(u))
                    .toList();

            return new TenantUsersListResponse(finalEntitlements, mapped);
        });
    }

    public List<TenantUserSummaryResponse> listEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () ->
                tenantUserTxService.listEnabledUsers(accountId)
                        .stream()
                        .map(tenantUserApiMapper::toSummary)
                        .toList()
        );
    }

    public TenantUserDetailsResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser user = tenantUserTxService.getUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public TenantUserSummaryResponse setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            tenantUserTxService.setSuspendedByAdmin(accountId, userId, suspended);
            TenantUser updated = tenantUserTxService.getUser(userId, accountId);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public TenantUserSummaryResponse setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            tenantUserTxService.setSuspendedByAccount(accountId, userId, suspended);
            TenantUser updated = tenantUserTxService.getUser(userId, accountId);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        tenantExecutor.run(schema, () -> tenantUserTxService.softDelete(userId, accountId));
    }

    public TenantUserSummaryResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser restored = tenantUserTxService.restore(userId, accountId);
            return tenantUserApiMapper.toSummary(restored);
        });
    }

    public TenantUserSummaryResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser updated = tenantUserTxService.resetPassword(userId, accountId, newPassword);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        tenantExecutor.run(schema, () -> tenantUserTxService.hardDelete(userId, accountId));
    }

    // =========================================================
    // PASSWORD RESET (PUBLIC -> TENANT)
    // =========================================================

    public String generatePasswordResetToken(String slug, String email) {
        if (!StringUtils.hasText(slug)) throw new ApiException("INVALID_SLUG", "Slug é obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_LOGIN", "Email é obrigatório", 400);

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(slug);

        return tenantExecutor.run(account.schemaName(), () -> {
            TenantUser user = tenantUserTxService.getUserByEmail(email, account.id());

            if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            String token = jwtTokenProvider.generatePasswordResetToken(
                    user.getEmail(),
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

        String email = jwtTokenProvider.getEmailFromToken(token);

        tenantExecutor.run(schema, () ->
                tenantUserTxService.resetPasswordWithToken(accountId, email, token, newPassword)
        );
    }

    // =========================================================
    // MY PROFILE
    // =========================================================

    public TenantMeResponse updateMyProfile(UpdateMyProfileRequest req) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.run(schema, () -> {
            TenantUser updated = tenantUserTxService.updateProfile(
                    userId,
                    accountId,
                    req.name(),
                    req.phone(),
                    req.avatarUrl(),
                    req.locale(),
                    req.timezone(),
                    appClock.now()
            );
            return tenantUserApiMapper.toMe(updated);
        });
    }

    public TenantUserDetailsResponse getEnabledTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser user = tenantUserTxService.getEnabledUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public long countEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> tenantUserTxService.countEnabledUsersByAccount(accountId));
    }
}
