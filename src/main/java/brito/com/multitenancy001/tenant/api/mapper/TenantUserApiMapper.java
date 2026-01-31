package brito.com.multitenancy001.tenant.api.mapper;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.tenant.api.dto.me.TenantMeResponse;
import brito.com.multitenancy001.tenant.api.dto.users.*;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class TenantUserApiMapper {

    private static SystemRoleName toSystemRoleOrNull(Object tenantRoleEnum) {
        if (tenantRoleEnum == null) return null;
        // ✅ assume nomes compatíveis (TENANT_*)
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

    public TenantUserDetailsResponse toDetails(TenantUser tenantUser) {
        boolean enabled =
                !tenantUser.isDeleted()
                        && !tenantUser.isSuspendedByAccount()
                        && !tenantUser.isSuspendedByAdmin();

        SystemRoleName role = toSystemRoleOrNull(tenantUser.getRole());

        return new TenantUserDetailsResponse(
                tenantUser.getId(),
                tenantUser.getAccountId(),
                tenantUser.getName(),
                tenantUser.getEmail(),
                role,
                tenantUser.getPhone(),
                tenantUser.getAvatarUrl(),
                tenantUser.getTimezone(),
                tenantUser.getLocale(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                tenantUser.isDeleted(),
                enabled
        );
    }

    public TenantMeResponse toMe(TenantUser tenantUser) {
        SystemRoleName role = toSystemRoleOrNull(tenantUser.getRole());

        return new TenantMeResponse(
                tenantUser.getId(),
                tenantUser.getAccountId(),
                tenantUser.getName(),
                tenantUser.getEmail(),
                role,
                tenantUser.getPhone(),
                tenantUser.getAvatarUrl(),
                tenantUser.getTimezone(),
                tenantUser.getLocale(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                tenantUser.isDeleted(),
                tenantUser.isEnabled()
        );
    }

    // =========================================================
    // ✅ NOVOS MAPPERS PARA /api/tenant/users
    // =========================================================

    /**
     * Para não-owner: retorna somente o básico (sem RBAC/audit).
     */
    public TenantUserListItemResponse toListItemBasic(TenantUser u) {
        boolean enabled = u.isEnabled();

        return new TenantUserListItemResponse(
                u.getId(),
                u.getEmail(),
                null,
                null,
                null,
                null,
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                enabled
        );
    }

    /**
     * Para TENANT_OWNER: retorna visão rica.
     */
    public TenantUserListItemResponse toListItemRich(TenantUser u) {
        boolean enabled = u.isEnabled();

        List<String> perms = (u.getPermissions() == null)
                ? List.of()
                : u.getPermissions().stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .toList();

        TenantActorRef createdBy = mapCreatedBy(u.getAudit());

        SystemRoleName role = toSystemRoleOrNull(u.getRole());

        return new TenantUserListItemResponse(
                u.getId(),
                u.getEmail(),
                role,
                perms,
                u.getLastLogin(),
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
