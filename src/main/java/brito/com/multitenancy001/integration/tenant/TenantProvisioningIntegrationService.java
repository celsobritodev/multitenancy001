package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
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
 * - Sempre faz "schema switch" via TenantExecutor.
 * - Não contém regra de negócio; regras ficam no Tenant (app).
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningIntegrationService {

    private final TenantExecutor tenantExecutor;
    private final TenantUserAdminTxService tenantUserAdminTxService;

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

        tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserAdminTxService.setSuspendedByAdmin(accountId, userId, suspended)
        );
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
     * ⚠️ Provisionamento de TENANT_OWNER:
     * No seu HEAD atual, isso NÃO existe no TenantUserAdminTxService.
     *
     * Próximo passo: localizar o serviço correto de provisioning no Tenant
     * (ex.: TenantUserProvisioning / onboarding worker) e delegar para ele aqui.
     */
    public void createTenantOwner(
            String tenantSchema,
            Long accountId,
            String name,
            String email,
            String rawPassword
    ) {
    	throw new ApiException(
    	        ApiErrorCode.FEATURE_NOT_IMPLEMENTED,
    	        "createTenantOwner deve delegar para o serviço de provisioning do Tenant.",
    	        501
    	);

    }

    // =========================================================
    // Guards (accountId/userId). tenantSchema é validado pelo TenantExecutor.
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
