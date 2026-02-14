package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.users.app.TenantUserAdminTxService;
import lombok.RequiredArgsConstructor;

/**
 * Fronteira explícita de integração: ControlPlane -> Tenant.
 *
 * Regras:
 * - ControlPlane não importa tenant.* diretamente.
 * - integration.* pode depender de tenant.* para executar casos de uso do contexto Tenant.
 *
 * Importante:
 * - Este service SEMPRE faz "schema switch" via TenantSchemaExecutor.
 * - As regras de negócio ficam dentro do Tenant (app), não aqui.
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningIntegrationService {

    private final TenantSchemaExecutor tenantExecutor;
    private final TenantUserAdminTxService tenantUserAdminTxService;

    /**
     * Lista summaries de usuários do tenant para consumo do ControlPlane.
     *
     * @param tenantSchema schema alvo do tenant
     * @param accountId accountId (obrigatório)
     * @param onlyOperational true = apenas usuários "operacionais" (não deletados e não suspensos)
     */
    public List<UserSummaryData> listUserSummaries(
            String tenantSchema,
            Long accountId,
            boolean onlyOperational
    ) {
        requireTenantSchema(tenantSchema);
        requireAccountId(accountId);

        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.listUserSummaries(accountId, onlyOperational)
        );
    }

    /**
     * Suspensão manual por admin (um único usuário).
     *
     * @param tenantSchema schema alvo do tenant
     * @param accountId accountId (obrigatório)
     * @param userId tenant user id (obrigatório)
     * @param suspended true para suspender, false para reativar
     */
    public void setSuspendedByAdmin(
            String tenantSchema,
            Long accountId,
            Long userId,
            boolean suspended
    ) {
        requireTenantSchema(tenantSchema);
        requireAccountId(accountId);
        requireUserId(userId);

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserAdminTxService.setSuspendedByAdmin(accountId, userId, suspended);
            return null;
        });
    }

    /**
     * Bulk admin: suspende todos menos TENANT_OWNER (regras no Tenant).
     */
    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        requireTenantSchema(tenantSchema);
        requireAccountId(accountId);

        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.suspendAllUsersByAccount(accountId)
        );
    }

    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        requireTenantSchema(tenantSchema);
        requireAccountId(accountId);

        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.unsuspendAllUsersByAccount(accountId)
        );
    }

    public int softDeleteAllUsersByAccount(String tenantSchema, Long accountId) {
        requireTenantSchema(tenantSchema);
        requireAccountId(accountId);

        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.softDeleteAllUsersByAccount(accountId)
        );
    }

    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        requireTenantSchema(tenantSchema);
        requireAccountId(accountId);

        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.restoreAllUsersByAccount(accountId)
        );
    }

    /**
     * ⚠️ Provisionamento de TENANT_OWNER:
     * No HEAD atual, isso NÃO pertence ao TenantUserAdminTxService.
     *
     * Próximo passo: apontar para o serviço de provisioning correto (ex.: TenantUserProvisioningService)
     * sem misturar regras aqui.
     */
    public void createTenantOwner(
            String tenantSchema,
            Long accountId,
            String name,
            String email,
            String rawPassword
    ) {
        throw new ApiException(
                ApiErrorCode.NOT_IMPLEMENTED,
                "createTenantOwner deve delegar para o serviço de provisioning do Tenant (não TenantUserAdminTxService).",
                501
        );
    }

    // =========================================================
    // Guards
    // =========================================================

    private void requireTenantSchema(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new ApiException(ApiErrorCode.TENANT_SCHEMA_REQUIRED, "tenantSchema obrigatório", 400);
        }
    }

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
