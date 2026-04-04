package brito.com.multitenancy001.tenant.users.app.command;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Regras de mutação de usuários do tenant.
 */
@Component
@RequiredArgsConstructor
public class TenantUserMutationGuard {

    private final TenantUserRepository tenantUserRepository;

    public void requireNotBuiltInForMutation(TenantUser user, String message) {
        if (user != null && user.getOrigin() == EntityOrigin.BUILT_IN) {
            throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, message);
        }
    }

    public boolean isActiveOwner(TenantUser user) {
        if (user == null) {
            return false;
        }
        if (user.isDeleted()) {
            return false;
        }
        if (user.isSuspendedByAccount()) {
            return false;
        }
        if (user.isSuspendedByAdmin()) {
            return false;
        }
        return user.getRole() != null && user.getRole().isTenantOwner();
    }

    public void requireWillStillHaveAtLeastOneActiveOwner(Long accountId, String message) {
        long owners = tenantUserRepository.countActiveOwnersByAccountId(accountId, TenantRole.TENANT_OWNER);
        if (owners <= 1) {
            throw new ApiException(ApiErrorCode.TENANT_OWNER_REQUIRED, message, 409);
        }
    }
}