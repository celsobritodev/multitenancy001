package brito.com.multitenancy001.integration.tenant;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaProvisioningService;
import lombok.RequiredArgsConstructor;

/**
 * Integração ControlPlane -> Infra (provisionamento/migração de schema).
 *
 * Regra: controlplane.* NÃO importa infrastructure.*
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningIntegrationService {

    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;

    /**
     * Garante que o schema exista e que as migrations do tenant rodem.
     * Retorna true se o schema está pronto.
     */
    public boolean ensureSchemaExistsAndMigrate(String tenantSchema) {
        return tenantSchemaProvisioningService.ensureSchemaExistsAndMigrate(tenantSchema);
    }

    /**
     * Alias de compatibilidade (caso algum código antigo ainda chame).
     * Mantemos o nome antigo, mas a semântica real é "ensure + migrate".
     */
    @Deprecated
    public boolean provisionTenantSchema(String tenantSchema) {
        return ensureSchemaExistsAndMigrate(tenantSchema);
    }

    public void tryDropSchema(String tenantSchema) {
        tenantSchemaProvisioningService.tryDropSchema(tenantSchema);
    }
}
