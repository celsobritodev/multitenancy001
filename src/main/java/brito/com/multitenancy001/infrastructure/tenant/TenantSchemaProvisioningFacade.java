package brito.com.multitenancy001.infrastructure.tenant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantSchemaProvisioningFacade {

    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;

    /**
     * Account.schemaName é o identificador persistido do schema do tenant.
     * tenantSchema é o mesmo valor, usado como contexto de execução na infraestrutura.
     */
    public boolean ensureSchemaExistsAndMigrate(String tenantSchema) {
        return tenantSchemaProvisioningService.ensureSchemaExistsAndMigrate(tenantSchema);
    }

    public void tryDropSchema(String tenantSchema) {
        tenantSchemaProvisioningService.tryDropSchema(tenantSchema);
    }
}
