package brito.com.multitenancy001.tenant.application.user;

import java.sql.PreparedStatement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.domain.user.permission.TenantUserPermission;

@Component
public class TenantUserPermissionBulkInserter {

    private final JdbcTemplate jdbcTemplate;

    public TenantUserPermissionBulkInserter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(Long tenantUserId, List<TenantUserPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                "INSERT INTO tenant_user_permissions (tenant_user_id, permission) VALUES (?, ?)",
                permissions,
                500,
                (PreparedStatement ps, TenantUserPermission perm) -> {
                    ps.setLong(1, tenantUserId);
                    ps.setString(2, perm.code()); // ✅ AQUI é code(), não name()
                }
        );
    }
}
