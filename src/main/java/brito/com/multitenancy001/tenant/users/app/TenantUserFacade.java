package brito.com.multitenancy001.tenant.users.app;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantUserFacade {

    private final TenantUserApiMapper tenantUserApiMapper;

    private final TenantUserService tenantUserService;
    private final AccountResolver accountResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;
    private final AccountEntitlementsGuard accountEntitlementsGuard;

    private final AccountEntitlementsService accountEntitlementsService;

    private final TenantExecutor tenantExecutor;

    private final SecurityAuditService securityAuditService;

    // =========================================================
    // CONTROLLER METHODS
    // =========================================================

    public void transferTenantOwner(Long toUserId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long fromUserId = securityUtils.getCurrentUserId();

        tenantExecutor.run(schema, (java.util.function.Supplier<Void>) () -> {
            tenantUserService.transferTenantOwnerRole(accountId, fromUserId, toUserId);
            return null;
        });
    }

    public TenantMeResponse getMyProfile() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.run(schema, () -> {
            TenantUser user = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toMe(user);
        });
    }

    public TenantUserDetailsResponse createTenantUser(TenantUserCreateRequest req) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);

        String name = (req.name() == null) ? null : req.name().trim();
        String email = (req.email() == null) ? null : req.email().trim().toLowerCase();

        String locale = (req.locale() == null) ? null : req.locale().trim();
        if (locale != null && locale.isBlank()) locale = null;

        String timezone = (req.timezone() == null) ? null : req.timezone().trim();
        if (timezone != null && timezone.isBlank()) timezone = null;

        final LinkedHashSet<String> perms =
                (req.permissions() == null || req.permissions().isEmpty())
                        ? null
                        : new LinkedHashSet<>(req.permissions());

        EntityOrigin origin = (req.origin() != null) ? req.origin() : EntityOrigin.ADMIN;
        if (origin == EntityOrigin.BUILT_IN) {
            throw new ApiException("INVALID_ORIGIN", "Origin BUILT_IN não pode ser criado via API", 400);
        }

        Boolean mustChangePassword = (req.mustChangePassword() == null) ? Boolean.FALSE : req.mustChangePassword();

        long currentUsers = tenantExecutor.run(schema, () ->
                tenantUserService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE)
        );

        accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);

        String finalLocale = locale;
        String finalTimezone = timezone;

        return tenantExecutor.run(schema, () -> {
            TenantUser created = tenantUserService.createTenantUser(
                    accountId,
                    name,
                    email,
                    req.password(),
                    req.role(),
                    req.phone(),
                    req.avatarUrl(),
                    finalLocale,
                    finalTimezone,
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
            List<TenantUser> users = tenantUserService.listUsers(accountId);

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
                tenantUserService.listEnabledUsers(accountId)
                        .stream()
                        .map(tenantUserApiMapper::toSummary)
                        .toList()
        );
    }

    public TenantUserDetailsResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser user = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public TenantUserSummaryResponse setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            tenantUserService.setSuspendedByAdmin(accountId, userId, suspended);
            TenantUser updated = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public TenantUserSummaryResponse setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            tenantUserService.setSuspendedByAccount(accountId, userId, suspended);
            TenantUser updated = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        tenantExecutor.run(schema, (java.util.function.Supplier<Void>) () -> {
            tenantUserService.softDelete(userId, accountId);
            return null;
        });
    }

    public TenantUserSummaryResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser restored = tenantUserService.restore(userId, accountId);
            return tenantUserApiMapper.toSummary(restored);
        });
    }

    public TenantUserSummaryResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser updated = tenantUserService.resetPassword(userId, accountId, newPassword);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        tenantExecutor.run(schema, (java.util.function.Supplier<Void>) () -> {
            tenantUserService.hardDelete(userId, accountId);
            return null;
        });
    }

    // =========================================================
    // PASSWORD RESET (PUBLIC -> TENANT) + SECURITY AUDIT
    // =========================================================

    public String generatePasswordResetToken(String slug, String email) {
        if (!StringUtils.hasText(slug)) throw new ApiException("INVALID_SLUG", "Slug é obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_LOGIN", "Email é obrigatório", 400);

        securityAuditService.record(
                "PASSWORD_RESET_REQUESTED",
                "ATTEMPT",
                null,
                null,
                email,
                null,
                null,
                null,
                "{\"slug\":\"" + slug + "\"}"
        );

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(slug);

        try {
            String token = tenantExecutor.run(account.schemaName(), () -> {
                TenantUser user = tenantUserService.getUserByEmail(email, account.id());

                if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                    throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
                }

                String t = jwtTokenProvider.generatePasswordResetToken(
                        user.getEmail(),
                        account.schemaName(),
                        account.id()
                );

                user.setPasswordResetToken(t);

                // ✅ Instant não tem plusHours; use Duration
                user.setPasswordResetExpires(appClock.instant().plus(Duration.ofHours(1)));

                tenantUserService.save(user);
                return t;
            });

            securityAuditService.record(
                    "PASSWORD_RESET_REQUESTED",
                    "SUCCESS",
                    null,
                    null,
                    email,
                    null,
                    account.id(),
                    account.schemaName(),
                    "{\"expiresHours\":1}"
            );

            return token;

        } catch (Exception e) {
            securityAuditService.record(
                    "PASSWORD_RESET_REQUESTED",
                    "FAILURE",
                    null,
                    null,
                    email,
                    null,
                    account.id(),
                    account.schemaName(),
                    "{\"reason\":\"error\"}"
            );
            throw e;
        }
    }

    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) throw new ApiException("INVALID_TOKEN", "Token inválido", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);

        String schema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);

        securityAuditService.record(
                "PASSWORD_RESET_COMPLETED",
                "ATTEMPT",
                null,
                null,
                email,
                null,
                accountId,
                schema,
                "{\"stage\":\"start\"}"
        );

        try {
            tenantExecutor.run(schema, (java.util.function.Supplier<Void>) () -> {
                tenantUserService.resetPasswordWithToken(accountId, email, token, newPassword);
                return null;
            });

            securityAuditService.record(
                    "PASSWORD_RESET_COMPLETED",
                    "SUCCESS",
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    schema,
                    "{\"stage\":\"done\"}"
            );
        } catch (Exception e) {
            securityAuditService.record(
                    "PASSWORD_RESET_COMPLETED",
                    "FAILURE",
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    schema,
                    "{\"reason\":\"error\"}"
            );
            throw e;
        }
    }

    // =========================================================
    // MY PROFILE
    // =========================================================

    public TenantMeResponse updateMyProfile(UpdateMyProfileRequest req) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.run(schema, () -> {
            TenantUser updated = tenantUserService.updateProfile(
                    userId,
                    accountId,
                    req.name(),
                    req.phone(),
                    req.avatarUrl(),
                    req.locale(),
                    req.timezone(),
                    appClock.instant()
            );
            return tenantUserApiMapper.toMe(updated);
        });
    }

    public TenantUserDetailsResponse getEnabledTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> {
            TenantUser user = tenantUserService.getEnabledUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public long countEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schema = securityUtils.getCurrentSchema();

        return tenantExecutor.run(schema, () -> tenantUserService.countEnabledUsersByAccount(accountId));
    }
}
