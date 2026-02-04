package brito.com.multitenancy001.tenant.users.api.mapper;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantActorRef;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class TenantUserApiMapper {

    private static SystemRoleName toSystemRoleOrNull(Object tenantRoleEnum) {
        if (tenantRoleEnum == null) return null;
        return SystemRoleName.fromString(tenantRoleEnum.toString());
    }

    public TenantUserSummaryResponse toSummary(TenantUser tenantUser) {
        boolean enabled =
                !tenantUser.isDeleted()
                        && !tenantUser.isSuspendedByAccount()
                        && !tenantUser.isSuspendedByAdmin();

        return new TenantUserSummaryResponse(
                tenantUser.getId(),
                tenantUser.getEmail(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }

    public TenantMeResponse toMe(TenantUser u) {
        boolean enabled = u.isEnabled();
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantMeResponse(
                u.getId(),
                u.getAccountId(),
                u.getName(),
                u.getEmail(),
                role,
                u.getPhone(),
                u.getAvatarUrl(),
                u.getTimezone(),
                u.getLocale(),
                u.isMustChangePassword(),
                u.getOrigin(),
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                u.isDeleted(),
                enabled
        );
    }

    /**
     * ✅ Assinatura REAL do seu DTO TenantUserDetailsResponse (sem permissions / audit extras).
     */
    public TenantUserDetailsResponse toDetails(TenantUser u) {
        boolean enabled = u.isEnabled();
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserDetailsResponse(
                u.getId(),
                u.getAccountId(),
                u.getName(),
                u.getEmail(),
                role,
                u.getPhone(),
                u.getAvatarUrl(),
                u.getTimezone(),
                u.getLocale(),
                u.isMustChangePassword(),
                u.getOrigin(),
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                u.isDeleted(),
                enabled
        );
    }

    /**
     * ✅ Lista básica (não-owner): sem permissions/audit; mantém shape do DTO.
     */
    public TenantUserListItemResponse toListItemBasic(TenantUser u) {
        boolean enabled = u.isEnabled();
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserListItemResponse(
                u.getId(),
                u.getEmail(),
                role,
                List.of(),
                u.isMustChangePassword(),
                u.getOrigin(),
                null,      // lastLoginAt (somente owner)
                null,      // createdBy (somente owner)
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                enabled
        );
    }

    /**
     * ✅ Lista rica (owner): inclui permissions + audit/meta.
     */
    public TenantUserListItemResponse toListItemRich(TenantUser u) {
        boolean enabled = u.isEnabled();

        List<String> perms = (u.getPermissions() == null)
                ? List.of()
                : u.getPermissions().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.trim().toUpperCase(Locale.ROOT))
                        .sorted(Comparator.naturalOrder())
                        .toList();

        TenantActorRef createdBy = mapCreatedBy(u.getAudit());
        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserListItemResponse(
                u.getId(),
                u.getEmail(),
                role,
                perms,
                u.isMustChangePassword(),
                u.getOrigin(),
                u.getLastLoginAt(),
                createdBy,
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                enabled
        );
    }

    private TenantActorRef mapCreatedBy(AuditInfo audit) {
        if (audit == null) return null;
        if (audit.getCreatedBy() == null && audit.getCreatedByEmail() == null) return null;
        return new TenantActorRef(audit.getCreatedBy(), audit.getCreatedByEmail());
    }
}
