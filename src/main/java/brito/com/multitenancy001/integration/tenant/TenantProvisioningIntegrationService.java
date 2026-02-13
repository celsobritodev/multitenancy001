package brito.com.multitenancy001.integration.tenant;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import lombok.RequiredArgsConstructor;

/**
 * Fronteira explícita de integração: ControlPlane -> Tenant (provisioning de usuário/admin).
 *
 * <p>
 * Regras de arquitetura (DDD/layered sem ports & adapters):
 * <ul>
 *   <li>controlplane.* depende de integration.* (e shared.*), nunca de tenant.* diretamente.</li>
 *   <li>integration.* pode depender de tenant.* para executar casos de uso do contexto Tenant.</li>
 * </ul>
 *
 * <p>
 * Esta classe encapsula chamadas do ControlPlane para o contexto Tenant, evitando "leaks" de bounded context.
 * O schema alvo é sempre informado (tenantSchema) por ser uma operação cross-context.
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningIntegrationService {

    private final TenantProvisioningIntegrationService tenantProvisioningIntegrationService;

    public UserSummaryData createTenantOwner(
            String tenantSchema,
            Long accountId,
            String accountDisplayName,
            String loginEmail,
            String rawPassword
    ) {
        // Entrada do método: createTenantOwner
        return tenantProvisioningIntegrationService.createTenantOwner(
                tenantSchema,
                accountId,
                accountDisplayName,
                loginEmail,
                rawPassword
        );
    }

    public List<UserSummaryData> listUserSummaries(
            String tenantSchema,
            Long accountId,
            boolean onlyOperational
    ) {
        // Entrada do método: listUserSummaries
        return tenantProvisioningIntegrationService.listUserSummaries(
                tenantSchema,
                accountId,
                onlyOperational
        );
    }

    public void setSuspendedByAdmin(
            String tenantSchema,
            Long accountId,
            Long userId,
            boolean suspended
    ) {
        // Entrada do método: setSuspendedByAdmin
        tenantProvisioningIntegrationService.setSuspendedByAdmin(
                tenantSchema,
                accountId,
                userId,
                suspended
        );
    }
}
