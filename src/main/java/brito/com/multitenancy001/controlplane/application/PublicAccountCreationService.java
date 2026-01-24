package brito.com.multitenancy001.controlplane.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountOrigin;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.domain.account.AccountType;
import brito.com.multitenancy001.controlplane.domain.account.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.shared.domain.DomainException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "publicTransactionManager")
public class PublicAccountCreationService {

    private final AccountRepository accountRepository;
    private final AccountEntitlementsProvisioningService accountEntitlementsProvisioningService;
    private final AppClock appClock;

    public Account createAccountFromSignup(SignupRequest signupRequest) {

        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String slug = generateSlug(signupRequest.displayName());
            String schemaName = generateSchemaName(signupRequest.displayName());

            try {
                Account account = new Account();
                account.setType(AccountType.TENANT);
                account.setOrigin(AccountOrigin.ADMIN);
                account.setDisplayName(signupRequest.displayName());
                account.setSlug(slug);
                account.setSchemaName(schemaName);

                account.setLoginEmail(signupRequest.loginEmail());
                account.setTaxIdType(signupRequest.taxIdType());
                account.setTaxIdNumber(signupRequest.taxIdNumber());

                // ✅ sem LocalDateTime variável -> sem import LocalDateTime
                account.setTrialEndDate(appClock.now().plusDays(30));
                account.setStatus(AccountStatus.FREE_TRIAL);
                account.setSubscriptionPlan(SubscriptionPlan.FREE);

                account.setCountry("Brasil");
                account.setTimezone("America/Sao_Paulo");
                account.setLocale("pt_BR");
                account.setCurrency("BRL");

                if (account.getType() == AccountType.PLATFORM
                        && accountRepository.existsByTypeAndDeletedFalse(AccountType.PLATFORM)) {
                    throw new DomainException("Only one PLATFORM account is allowed");
                }

                Account saved = accountRepository.save(account);

                accountEntitlementsProvisioningService.ensureDefaultEntitlementsForTenant(saved);

                return saved;

            } catch (DataIntegrityViolationException e) {
                log.warn("Tentativa {} falhou por conflito (slug/schema). Tentando novamente...", attempt);
            }
        }

        throw new RuntimeException("Falha ao criar conta após " + maxAttempts + " tentativas");
    }

    private String generateSlug(String displayName) {
        String base = displayName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("(^-+|-+$)", "");
        if (base.length() < 3) base = "tenant";
        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String generateSchemaName(String displayName) {
        String base = displayName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        base = base.replaceAll("(^_+|_+$)", "");
        if (base.length() < 3) base = "tenant";
        return "t_" + base + "_" + UUID.randomUUID().toString().substring(0, 6);
    }
}
