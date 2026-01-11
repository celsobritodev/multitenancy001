package brito.com.multitenancy001.tenant.application.provisioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;

    public void createSchemaAndMigrate(String schemaName) {
        tenantSchemaProvisioningService.ensureSchemaExistsAndMigrate(schemaName);
    }
}
