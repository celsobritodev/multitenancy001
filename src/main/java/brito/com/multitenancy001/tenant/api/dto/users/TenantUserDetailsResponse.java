package brito.com.multitenancy001.tenant.api.dto.users;

import java.time.LocalDateTime;
import java.util.List;

import brito.com.multitenancy001.tenant.domain.user.TenantUser;

public record TenantUserDetailsResponse(
        Long id,
        String username,
        String name,
        String email,
        String role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long accountId,
        List<String> permissions
) {
    public TenantUserDetailsResponse {
        if (permissions == null) permissions = List.of();
    }

    public static TenantUserDetailsResponse from(TenantUser u) {
        boolean enabled = !u.isDeleted() && !u.isSuspendedByAccount() && !u.isSuspendedByAdmin();
        return new TenantUserDetailsResponse(
                u.getId(),
                u.getUsername(),
                u.getName(),
                u.getEmail(),
                u.getRole() != null ? u.getRole().name() : null,
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                enabled,
                u.getCreatedAt(),
                u.getUpdatedAt(),
                u.getAccountId(),
                u.getPermissions()
        );
    }
}
