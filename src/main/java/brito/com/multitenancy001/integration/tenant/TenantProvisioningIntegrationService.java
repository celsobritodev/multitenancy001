package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.TenantUserOperations;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.provisioning.app.TenantUserProvisioningService;
import brito.com.multitenancy001.tenant.users.app.TenantUserAdminTxService;
import lombok.RequiredArgsConstructor;

/**
 * Fronteira explícita de integração: ControlPlane -> Tenant.
 * 
 * <p>Esta classe implementa o contrato {@link TenantUserOperations}, permitindo que
 * o Control Plane execute operações em usuários de tenants de forma desacoplada.</p>
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Atuar como adaptador entre o contexto Control Plane e o contexto Tenant.</li>
 *   <li>Gerenciar a troca de schema do tenant via {@link TenantExecutor}.</li>
 *   <li>Delegar a execução das regras de negócio para os serviços apropriados do Tenant.</li>
 * </ul>
 *
 * <p>Regras Arquiteturais:</p>
 * <ul>
 *   <li>O pacote {@code controlplane.*} NÃO pode importar classes deste serviço diretamente.
 *       Ele deve depender apenas da interface {@link TenantUserOperations}.</li>
 *   <li>Esta classe, por estar em {@code integration.tenant}, pode (e deve) conhecer
 *       os detalhes de implementação do Tenant.</li>
 *   <li>Toda operação que requer o contexto do tenant deve ser executada dentro de
 *       um bloco {@code tenantExecutor.runInTenantSchema(...)}.</li>
 * </ul>
 *
 * @see TenantUserOperations
 * @see TenantExecutor
 * @see TenantUserAdminTxService
 * @see TenantUserProvisioningService
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningIntegrationService implements TenantUserOperations {

    private final TenantExecutor tenantExecutor;

    // Tenant app services (executam dentro do schema do tenant)
    private final TenantUserAdminTxService tenantUserAdminTxService;

    // Provisioning (já cuida de readiness + tx + schema switch internamente)
    private final TenantUserProvisioningService tenantUserProvisioningService;

    @Override
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

    @Override
    public void setUserSuspendedByAdmin(
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

    @Override
    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.suspendAllUsersByAccount(accountId)
        );
    }

    @Override
    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.unsuspendAllUsersByAccount(accountId)
        );
    }

    @Override
    public int softDeleteAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.softDeleteAllUsersByAccount(accountId)
        );
    }

    @Override
    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        requireAccountId(accountId);
        return tenantExecutor.runInTenantSchema(tenantSchema,
                () -> tenantUserAdminTxService.restoreAllUsersByAccount(accountId)
        );
    }

    /**
     * Provisiona o usuário OWNER inicial (TENANT_OWNER) no schema do tenant.
     * 
     * <p><b>Nota:</b> Este método NÃO faz parte do contrato {@link TenantUserOperations}
     * porque é uma operação específica do fluxo de onboarding, que só é chamada
     * pelo Control Plane durante a criação de uma nova conta. Ela permanece aqui
     * como um serviço de integração especializado.</p>
     *
     * @param tenantSchema O schema do tenant alvo.
     * @param accountId    O ID da conta.
     * @param name         O nome do owner.
     * @param email        O email do owner.
     * @param rawPassword  A senha em texto puro.
     * @return Um resumo dos dados do usuário criado.
     * @throws ApiException Se os parâmetros forem inválidos ou o provisionamento falhar.
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