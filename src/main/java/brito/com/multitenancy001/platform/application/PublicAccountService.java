package brito.com.multitenancy001.platform.application;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.multitenancy.SchemaContext;
import brito.com.multitenancy001.platform.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;
import brito.com.multitenancy001.platform.persistence.publicdb.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "publicTransactionManager")
public class PublicAccountService {

    private final AccountRepository accountRepository;


  
    public TenantAccount createAccountFromSignup(SignupRequest request) {
    	 SchemaContext.unbindSchema();

    	    int maxAttempts = 5;
    	    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    	        String slug = generateSlug(request.name());
    	        String schemaName = generateSchemaName(request.name());

    	        try {
    	            TenantAccount account = new TenantAccount();
    	            account.setName(request.name());
    	            account.setSlug(slug);
    	            account.setSchemaName(schemaName);

    	            account.setCompanyEmail(request.companyEmail());

    	            // ✅ novos campos (sempre em conjunto)
    	            account.setCompanyDocType(request.companyDocType());
    	            account.setCompanyDocNumber(request.companyDocNumber());

    	            account.setCreatedAt(LocalDateTime.now());
    	            account.setTrialEndDate(LocalDateTime.now().plusDays(30));
    	            account.setStatus(TenantAccountStatus.FREE_TRIAL);
    	            account.setSystemAccount(false);

    	            // Defaults
    	            account.setSubscriptionPlan("FREE");
    	            account.setMaxUsers(5);
    	            account.setMaxProducts(100);
    	            account.setMaxStorageMb(100);
    	            account.setCompanyCountry("Brasil");
    	            account.setTimezone("America/Sao_Paulo");
    	            account.setLocale("pt_BR");
    	            account.setCurrency("BRL");

    	            return accountRepository.save(account);

    	        } catch (DataIntegrityViolationException e) {
    	            if (!isSlugOrSchemaUniqueViolation(e)) throw e;
    	            log.warn("⚠️ Colisão (tentativa {}/{}) | slug={} | schema={}",
    	                    attempt, maxAttempts, slug, schemaName);
    	        }
    	    }

    	    throw new ApiException("ACCOUNT_CREATE_FAILED",
    	            "Não foi possível criar conta (colisão de identificadores). Tente novamente.", 409);
    	}
    
    
    private String generateSlug(String name) {
        String base = (name == null ? "conta" : name.toLowerCase())
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        String slug = base;
        int i = 1;

        while (accountRepository.findBySlugAndDeletedFalse(slug).isPresent()) {
            slug = base + "-" + (i++);
        }
        return slug;
    }

    private String generateSchemaName(String name) {
        String base = (name == null ? "tenant" : name.toLowerCase())
                .replaceAll("[^a-z0-9]", "_");
        return "tenant_" + base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

   
    
    

    private boolean isSlugOrSchemaUniqueViolation(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        String msg = (t.getMessage() == null) ? "" : t.getMessage().toLowerCase();

        return msg.contains("ux_accounts_slug_active")
                || msg.contains("uk_accounts_slug")
                || msg.contains("uk_accounts_schema_name")
                || msg.contains("accounts_slug_key")
                || msg.contains("accounts_schema_name_key")
                || msg.contains("company_email");
    }

}
