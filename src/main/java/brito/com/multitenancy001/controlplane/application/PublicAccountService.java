package brito.com.multitenancy001.controlplane.application;

import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "publicTransactionManager")
public class PublicAccountService {

    private final AccountRepository accountRepository;
    private final AppClock appClock;

    public Account createAccountFromSignup(SignupRequest signupRequest) {
        TenantContext.clear();

        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String slug = generateSlug(signupRequest.name());
            String schemaName = generateSchemaName(signupRequest.name());

            try {
                LocalDateTime now = appClock.now();

                Account account = new Account();
                account.setName(signupRequest.name());
                account.setSlug(slug);
                account.setSchemaName(schemaName);

                account.setCompanyEmail(signupRequest.companyEmail());
                account.setCompanyDocType(signupRequest.companyDocType());
                account.setCompanyDocNumber(signupRequest.companyDocNumber());

                // ✅ negócio (clock-aware)
                account.setTrialEndDate(now.plusDays(30));
                account.setStatus(AccountStatus.FREE_TRIAL);
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

        throw new ApiException(
                "ACCOUNT_CREATE_FAILED",
                "Não foi possível criar conta (colisão de identificadores). Tente novamente.",
                409
        );
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
