package brito.com.multitenancy001.infrastructure.tenant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private final TenantSchemaProvisioningWorker tenantSchemaProvisioningWorker;

    /**
     * Account.tenantSchema é o identificador persistido do schema do tenant.
     * tenantSchema é o mesmo valor, usado como contexto de execução na infraestrutura.
     */
    public boolean ensureSchemaExistsAndMigrate(String tenantSchema) {
        return tenantSchemaProvisioningWorker.ensureSchemaExistsAndMigrate(tenantSchema);
    }

    public void tryDropSchema(String tenantSchema) {
        tenantSchemaProvisioningWorker.tryDropSchema(tenantSchema);
    }
}
