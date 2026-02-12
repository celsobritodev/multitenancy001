package brito.com.multitenancy001.tenant.users.app;

import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class TenantUserService {

    private final TenantUserCommandService commandService;
    private final TenantUserQueryService queryService;

    // =========================================================
    // LIMITS / COUNTS
    // =========================================================

    public long countUsersForLimit(Long accountId, UserLimitPolicy policy) {
        return queryService.countUsersForLimit(accountId, policy);
    }

    public long countEnabledUsersByAccount(Long accountId) {
        return queryService.countEnabledUsersByAccount(accountId);
    }

    // =========================================================
    // CREATE
    // =========================================================

    public TenantUser createTenantUser(
            Long accountId,
            String name,
            String email,
            String rawPassword,
            TenantRole role,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            LinkedHashSet<TenantPermission> requestedPermissions,
            Boolean mustChangePassword,
            EntityOrigin origin
    ) {
        return commandService.createTenantUser(
                accountId, name, email, rawPassword, role,
                phone, avatarUrl, locale, timezone,
                requestedPermissions, mustChangePassword, origin
        );
    }

    // =========================================================
    // READ / LIST
    // =========================================================

    public TenantUser getUser(Long userId, Long accountId) {
        return queryService.getUser(userId, accountId);
    }

    public TenantUser getEnabledUser(Long userId, Long accountId) {
        return queryService.getEnabledUser(userId, accountId);
    }

    public TenantUser getUserByEmail(String email, Long accountId) {
        return queryService.getUserByEmail(email, accountId);
    }

    public List<TenantUser> listUsers(Long accountId) {
        return queryService.listUsers(accountId);
    }

    public List<TenantUser> listEnabledUsers(Long accountId) {
        return queryService.listEnabledUsers(accountId);
    }

    // =========================================================
    // UPDATE: STATUS / PROFILE / PASSWORD
    // =========================================================

    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        commandService.setSuspendedByAdmin(accountId, userId, suspended);
    }

    public void setSuspendedByAccount(Long accountId, Long userId, boolean suspended) {
        commandService.setSuspendedByAccount(accountId, userId, suspended);
    }

    public TenantUser updateProfile(Long userId, Long accountId, String name, String phone, String avatarUrl, String locale, String timezone, Instant now) {
        return commandService.updateProfile(userId, accountId, name, phone, avatarUrl, locale, timezone, now);
    }

    public TenantUser resetPassword(Long userId, Long accountId, String newPassword) {
        return commandService.resetPassword(userId, accountId, newPassword);
    }

    public void resetPasswordWithToken(Long accountId, String email, String token, String newPassword) {
        commandService.resetPasswordWithToken(accountId, email, token, newPassword);
    }

    public void changeMyPassword(Long userId, Long accountId, String currentPassword, String newPassword, String confirmNewPassword) {
        commandService.changeMyPassword(userId, accountId, currentPassword, newPassword, confirmNewPassword);
    }

    // =========================================================
    // DELETE / RESTORE
    // =========================================================

    public void softDelete(Long userId, Long accountId) {
        commandService.softDelete(userId, accountId);
    }

    public TenantUser restore(Long userId, Long accountId) {
        return commandService.restore(userId, accountId);
    }

    public void hardDelete(Long userId, Long accountId) {
        commandService.hardDelete(userId, accountId);
    }

    public TenantUser save(TenantUser user) {
        return commandService.save(user);
    }

    // =========================================================
    // ROLE TRANSFER (OWNER)
    // =========================================================

    public void transferTenantOwnerRole(Long accountId, Long fromUserId, Long toUserId) {
        commandService.transferTenantOwnerRole(accountId, fromUserId, toUserId);
    }
}
