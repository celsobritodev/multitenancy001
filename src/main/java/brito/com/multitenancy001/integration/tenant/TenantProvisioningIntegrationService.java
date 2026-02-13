package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.tenant.users.app.TenantUserAdminTxService;
import lombok.RequiredArgsConstructor;

/**
 * Fronteira explícita de integração: ControlPlane -> Tenant.
 *
 * Regras:
 * - controlplane.* depende de integration.* e shared.*, nunca de tenant.* diretamente.
 * - integration.* pode depender de tenant.* para executar casos de uso do contexto Tenant.
 *
 * Observação:
 * - A troca de schema é responsabilidade desta fronteira (cross-context/cross-tenant).
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningIntegrationService {

    private final TenantExecutor tenantSchemaExecutor;
    private final TenantUserAdminTxService tenantUserAdminTxService;

    /**
     * Lista usuários do tenant (resumo) no schema informado.
     */
    public List<UserSummaryData> listUserSummaries(String tenantSchema, boolean onlyOperational) {
        return tenantSchemaExecutor.runInTenantSchema(
                tenantSchema,
                () -> tenantUserAdminTxService.listUserSummaries(onlyOperational)
        );
    }

    /**
     * Suspende/reativa usuário via ação administrativa do ControlPlane.
     */
    public void setSuspendedByAdmin(String tenantSchema, Long userId, boolean suspended) {
        tenantSchemaExecutor.runInTenantSchema(
                tenantSchema,
                () -> {
                    tenantUserAdminTxService.setSuspendedByAdmin(userId, suspended);
                    return null;
                }
        );
    }

    /**
     * Provisiona o owner/admin do tenant (signup/admin create).
     * Mantive aqui porque você mencionou esse caso antes.
     * Se o seu tenant provisioning for em outro service (TenantUserProvisioningService),
     * me mande o arquivo e eu ajusto para o seu nome real.
     */
    public UserSummaryData createTenantOwner(
            String tenantSchema,
            Long accountId,
            String accountDisplayName,
            String loginEmail,
            String rawPassword
    ) {
        return tenantSchemaExecutor.runInTenantSchema(
                tenantSchema,
                () -> tenantUserAdminTxService.createTenantOwner(accountId, accountDisplayName, loginEmail, rawPassword)
        );
    }
}
