package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.provisioning.app.TenantUserProvisioningService;
import brito.com.multitenancy001.tenant.users.app.TenantUserAdminTxService;
import lombok.RequiredArgsConstructor;

/**
 * Fronteira explícita de integração: ControlPlane -> Tenant.
 *
 * Regras:
 * - controlplane.* NÃO importa tenant.* diretamente.
 * - integration.* pode depender de tenant.* para executar casos de uso do contexto Tenant.
 *
 * Importante:
 * - Sempre faz "schema switch" via TenantExecutor quando chamar serviços que assumem contexto tenant.
 * - Não contém regra de negócio; regras ficam no Tenant (app).
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningIntegrationService {

    private final TenantExecutor tenantExecutor;

    // Tenant app services (executam dentro do schema do tenant)
    private final TenantUserAdminTxService tenantUserAdminTxService;

    // Provisioning (já cuida de readiness + tx + schema switch internamente)
    private final TenantUserProvisioningService tenantUserProvisioningService;

    public List<UserSummaryData> listUserSummaries(
            String tenantSchema,
            Long accountId,
            boolean onlyOperational
    ) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.listUserSummaries(accountId, onlyOperational)
        );
    }

    public void setSuspendedByAdmin(
            String tenantSchema,
            Long accountId,
            Long userId,
            boolean suspended
    ) {
        requireAccountId(accountId);
        requireUserId(userId);

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserAdminTxService.setSuspendedByAdmin(accountId, userId, suspended);
            return null;
        });
    }

    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.suspendAllUsersByAccount(accountId)
        );
    }

    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.unsuspendAllUsersByAccount(accountId)
        );
    }

    public int softDeleteAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.softDeleteAllUsersByAccount(accountId)
        );
    }

    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.restoreAllUsersByAccount(accountId)
        );
    }

    /**
     * ✅ Provisionamento do TENANT_OWNER (owner inicial).
     * Delegado para o TenantUserProvisioningService, que já garante readiness + tx.
     */
    public UserSummaryData createTenantOwner(
            String tenantSchema,
            Long accountId,
            String name,
            String email,
            String rawPassword
    ) {
        requireAccountId(accountId);

        // Este service já faz assertTenantSchemaReady + runInTenantSchema + tx internamente
        return tenantUserProvisioningService.createTenantOwner(
                tenantSchema,
                accountId,
                name,
                email,
                rawPassword
        );
    }

    // =========================================================
    // Guards
    // =========================================================

    private void requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId obrigatório", 400);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId obrigatório", 400);
        }
    }
}
