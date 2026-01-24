package brito.com.multitenancy001.infrastructure.tenant;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningFacade {

    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;

    public void ensureSchemaExistsAndMigrate(String schemaName) {
        tenantSchemaProvisioningService.ensureSchemaExistsAndMigrate(schemaName);
    }
}
