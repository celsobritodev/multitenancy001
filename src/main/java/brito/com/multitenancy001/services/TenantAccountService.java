package brito.com.multitenancy001.services;

import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;
import brito.com.multitenancy001.repositories.AccountRepository;
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
public class TenantAccountService {

    private final AccountRepository tenantAccountRepository;
    private final TenantSchemaService tenantSchemaService;

    @Transactional
    public TenantAccount createAccount(String name, String companyEmail, String companyDocument,
                                      String adminUsername, String adminEmail, String adminPassword) {

        TenantContext.unbindTenant(); // PUBLIC

        TenantAccount account = createAccountTx(name, companyEmail, companyDocument);

        try {
            // TENANT
            TenantContext.bindTenant(account.getSchemaName());
            tenantSchemaService.ensureSchemaAndMigrate(account.getSchemaName());

            // cria admin com JPA no schema bindado
            tenantSchemaService.createTenantAdmin(account, adminUsername, adminEmail, adminPassword);

            return account;

        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional
    protected TenantAccount createAccountTx(String name, String companyEmail, String companyDocument) {

        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String slug = generateSlug(name);
            String schemaName = generateSchemaName(slug);

            try {
                TenantAccount account = TenantAccount.builder()
                        .name(name)
                        .slug(slug)
                        .schemaName(schemaName)
                        .companyEmail(companyEmail)
                        .companyDocument(companyDocument)
                        .status(TenantAccountStatus.FREE_TRIAL)
                        .createdAt(LocalDateTime.now())
                        .trialEndDate(LocalDateTime.now().plusDays(30))
                        .systemAccount(false)
                        .build();

                return tenantAccountRepository.save(account);

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
