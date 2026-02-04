package brito.com.multitenancy001.infrastructure.tenant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantSchemaProvisioningFacade {

    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;

    public boolean ensureSchemaExistsAndMigrate(String schemaName) {
        return tenantSchemaProvisioningService.ensureSchemaExistsAndMigrate(schemaName);
    }

    public void tryDropSchema(String schemaName) {
        tenantSchemaProvisioningService.tryDropSchema(schemaName);
    }
}

