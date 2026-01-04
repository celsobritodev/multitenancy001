package brito.com.multitenancy001.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private final TenantSchemaProvisioningService tenantSchemaService;

    public void createSchemaAndMigrate(String schemaName) {
        tenantSchemaService.ensureSchemaAndMigrate(schemaName);
    }
}
