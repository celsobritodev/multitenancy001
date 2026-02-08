package brito.com.multitenancy001.tenant.users.app;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUsersListResponse;
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

    public void transferTenantOwner(Long toUserId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();
        Long fromUserId = securityUtils.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.transferTenantOwnerRole(accountId, fromUserId, toUserId);
            return null;
        });
    }

    public TenantMeResponse getMyProfile() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            TenantUser user = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toMe(user);
        });
    }

    public TenantUserDetailsResponse createTenantUser(TenantUserCreateRequest req) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        if (req == null) throw new ApiException("INVALID_REQUEST", "Request inválido", 400);

        String name = (req.name() == null) ? null : req.name().trim();
        String email = (req.email() == null) ? null : req.email().trim().toLowerCase();

        String locale = (req.locale() == null) ? null : req.locale().trim();
        if (locale != null && locale.isBlank()) locale = null;

        String timezone = (req.timezone() == null) ? null : req.timezone().trim();
        if (timezone != null && timezone.isBlank()) timezone = null;

        final LinkedHashSet<TenantPermission> perms =
                (req.permissions() == null || req.permissions().isEmpty())
                        ? null
                        : new LinkedHashSet<>(req.permissions());

        EntityOrigin origin = (req.origin() != null) ? req.origin() : EntityOrigin.ADMIN;
        if (origin == EntityOrigin.BUILT_IN) {
            throw new ApiException("INVALID_ORIGIN", "Origin BUILT_IN não pode ser criado via API", 400);
        }

        Boolean mustChangePassword = (req.mustChangePassword() == null) ? Boolean.FALSE : req.mustChangePassword();

        long currentUsers = tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE)
        );

        accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);

        String finalLocale = locale;
        String finalTimezone = timezone;

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
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

    public TenantUsersListResponse listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        TenantRole currentRole = securityUtils.getCurrentTenantRole();
        boolean isOwner = currentRole != null && currentRole.isTenantOwner();

        AccountEntitlementsSnapshot entitlements = null;
        if (isOwner) {
            entitlements = accountEntitlementsService.resolveEffectiveByAccountId(accountId);
        }
        AccountEntitlementsSnapshot finalEntitlements = entitlements;

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
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
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserService.listEnabledUsers(accountId)
                        .stream()
                        .map(tenantUserApiMapper::toSummary)
                        .toList()
        );
    }

    public TenantUserDetailsResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            TenantUser user = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public TenantUserSummaryResponse setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.setSuspendedByAdmin(accountId, userId, suspended);
            TenantUser updated = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public TenantUserSummaryResponse setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.setSuspendedByAccount(accountId, userId, suspended);
            TenantUser updated = tenantUserService.getUser(userId, accountId);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.softDelete(userId, accountId);
            return null;
        });
    }

    public TenantUserSummaryResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            TenantUser restored = tenantUserService.restore(userId, accountId);
            return tenantUserApiMapper.toSummary(restored);
        });
    }

    public TenantUserSummaryResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            TenantUser updated = tenantUserService.resetPassword(userId, accountId, newPassword);
            return tenantUserApiMapper.toSummary(updated);
        });
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.hardDelete(userId, accountId);
            return null;
        });
    }

    public String generatePasswordResetToken(String slug, String email) {
        if (!StringUtils.hasText(slug)) throw new ApiException("INVALID_SLUG", "Slug é obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_LOGIN", "Email é obrigatório", 400);

        securityAuditService.record(
                SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                AuditOutcome.ATTEMPT,
                null,
                null,
                email,
                null,
                null,
                null,
                "{\"slug\":\"" + slug + "\"}"
        );

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(slug);
        String tenantSchema = account.schemaName();

        try {
            String token = tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                TenantUser user = tenantUserService.getUserByEmail(email, account.id());

                if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                    throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
                }

                String passwordResetToken = jwtTokenProvider.generatePasswordResetToken(
                        user.getEmail(),
                        tenantSchema,
                        account.id()
                );

                user.setPasswordResetToken(passwordResetToken);
                user.setPasswordResetExpires(appClock.instant().plus(Duration.ofHours(1)));
                tenantUserService.save(user);
                return passwordResetToken;
            });

            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                    AuditOutcome.SUCCESS,
                    null,
                    null,
                    email,
                    null,
                    account.id(),
                    tenantSchema,
                    "{\"expiresHours\":1}"
            );

            return token;

        } catch (Exception e) {
            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                    AuditOutcome.FAILURE,
                    null,
                    null,
                    email,
                    null,
                    account.id(),
                    tenantSchema,
                    "{\"reason\":\"error\"}"
            );
            throw e;
        }
    }

    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) throw new ApiException("INVALID_TOKEN", "Token inválido", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException("INVALID_PASSWORD", "Nova senha é obrigatória", 400);

        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);

        securityAuditService.record(
                SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                AuditOutcome.ATTEMPT,
                null,
                null,
                email,
                null,
                accountId,
                tenantSchema,
                "{\"stage\":\"start\"}"
        );

        try {
            tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                tenantUserService.resetPasswordWithToken(accountId, email, token, newPassword);
                return null;
            });

            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    AuditOutcome.SUCCESS,
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    tenantSchema,
                    "{\"stage\":\"done\"}"
            );
        } catch (Exception e) {
            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    AuditOutcome.FAILURE,
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    tenantSchema,
                    "{\"reason\":\"error\"}"
            );
            throw e;
        }
    }

    public TenantMeResponse updateMyProfile(UpdateMyProfileRequest req) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
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
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            TenantUser user = tenantUserService.getEnabledUser(userId, accountId);
            return tenantUserApiMapper.toDetails(user);
        });
    }

    public long countEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> tenantUserService.countEnabledUsersByAccount(accountId));
    }

    public void changeMyPassword(TenantChangeMyPasswordRequest req) {
        if (req == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentSchema();
        Long userId = securityUtils.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserService.changeMyPassword(
                    userId,
                    accountId,
                    req.currentPassword(),
                    req.newPassword(),
                    req.confirmNewPassword()
            );
            return null;
        });
    }
}
