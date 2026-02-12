package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaExecutor;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.tenant.users.app.TenantUserAdminTxService;
import lombok.RequiredArgsConstructor;

/**
 * Camada explícita de INTEGRAÇÃO (Application Service cross-context).
 *
 * - É CONCRETO (sem port/interface).
 * - Vive fora de infrastructure.* para evitar dependência ControlPlane -> infrastructure.
 * - Faz o "schema switch" via TenantExecutor e delega regras para o Tenant (app).
 */
@Service
@RequiredArgsConstructor
public class TenantUsersIntegrationService {

    private final TenantSchemaExecutor tenantExecutor;
    private final TenantUserAdminTxService tenantUserAdminTxService;

    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.suspendAllUsersByAccount(accountId)
        );
    }

    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.unsuspendAllUsersByAccount(accountId)
        );
    }

    public int softDeleteAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.softDeleteAllUsersByAccount(accountId)
        );
    }

    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.restoreAllUsersByAccount(accountId)
        );
    }

    public List<UserSummaryData> listUserSummaries(String tenantSchema, Long accountId, boolean onlyOperational) {
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.listUserSummaries(accountId, onlyOperational)
        );
    }

    public void setSuspendedByAdmin(String tenantSchema, Long accountId, Long userId, boolean suspended) {
        tenantExecutor.runInTenantSchema(tenantSchema,
                () -> {
                    tenantUserAdminTxService.setSuspendedByAdmin(accountId, userId, suspended);
                    return null;
                }
        );
    }
}
