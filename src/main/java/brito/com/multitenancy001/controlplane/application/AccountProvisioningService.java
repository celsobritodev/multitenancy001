package brito.com.multitenancy001.controlplane.application;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.application.provisioning.TenantSchemaProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private final AccountRepository accountRepository;
    private final TenantSchemaProvisioningService tenantSchemaProvisionService;

    @Transactional
    public Account createAccount(String name, String companyEmail, String companyDocNumber,
                                      String adminUsername, String adminEmail, String adminPassword) {

        TenantSchemaContext.clearTenantSchema(); // PUBLIC

        Account account = createAccountTx(name, companyEmail, companyDocNumber);

        try {
            // TENANT
            TenantSchemaContext.bindTenantSchema(account.getSchemaName());
            tenantSchemaProvisionService.schemaMigrationService(account.getSchemaName());

            // cria admin com JPA no schema bindado
            tenantSchemaProvisionService.tenantAdminBootstrapService(account, adminUsername, adminEmail, adminPassword);

            return account;

        } finally {
            TenantSchemaContext.clearTenantSchema();
        }
    }

    @Transactional
    protected Account createAccountTx(String name, String companyEmail, String companyDocNumber) {

        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String slug = generateSlug(name);
            String schemaName = generateSchemaName(slug);

            try {
                Account account = Account.builder()
                        .name(name)
                        .slug(slug)
                        .schemaName(schemaName)
                        .companyEmail(companyEmail)
                        .companyDocNumber(companyDocNumber)
                        .status(AccountStatus.FREE_TRIAL)
                        .createdAt(LocalDateTime.now())
                        .trialEndDate(LocalDateTime.now().plusDays(30))
                        .systemAccount(false)
                        .build();

                return accountRepository.save(account);

            } catch (DataIntegrityViolationException e) {
                log.warn("⚠️ collision attempt {}/{} | slug={} schema={}", attempt, maxAttempts, slug, schemaName);
                if (attempt == maxAttempts) {
                    throw new ApiException("ACCOUNT_CREATE_FAILED",
                            "Não foi possível criar conta (colisão). Tente novamente.", 409);
                }
            }
        }

        throw new ApiException("ACCOUNT_CREATE_FAILED", "Falha inesperada ao criar conta.", 500);
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base.isBlank() ? "tenant" : base;
    }

    private String generateSchemaName(String slug) {
        return "tenant_" + slug.replace("-", "_") + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
