package brito.com.multitenancy001.controlplane.signup.app;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.AccountFactory;
import brito.com.multitenancy001.controlplane.accounts.app.command.CreateAccountCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo lifecycle da Account no public schema
 * durante o onboarding.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar Account em estado de provisioning.</li>
 *   <li>Garantir tenant schema inicial na entidade.</li>
 *   <li>Finalizar provisioning e definir trial inicial.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountProvisioningLifecycleService {

    private static final long DEFAULT_TRIAL_DAYS = 14L;

    private final AccountRepository accountRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;

    /**
     * Cria uma Account em estado PROVISIONING no public schema.
     *
     * @param signupData dados já validados e normalizados
     * @return account persistida
     */
    public Account createProvisioningAccount(AccountOnboardingSupport.SignupData signupData) {
        return publicSchemaUnitOfWork.tx(() -> {
            CreateAccountCommand createAccountCommand = new CreateAccountCommand(
                    signupData.displayName(),
                    signupData.loginEmail(),
                    signupData.taxCountryCode(),
                    signupData.taxIdType(),
                    signupData.taxIdNumber()
            );

            Account created = AccountFactory.newTenantAccount(createAccountCommand);
            created.setStatus(AccountStatus.PROVISIONING);
            created.ensureTenantSchema();

            return accountRepository.save(created);
        });
    }

    /**
     * Finaliza o provisioning da Account no public schema.
     *
     * @param accountId id da account
     * @return account finalizada
     */
    public Account finalizeProvisioning(Long accountId) {
        return publicSchemaUnitOfWork.tx(() -> {
            Account managed = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada após criação",
                            500
                    ));

            Instant now = appClock.instant();

            if (managed.getStatus() == AccountStatus.PROVISIONING) {
                managed.setStatus(AccountStatus.FREE_TRIAL);
            }

            if (managed.getStatus() == AccountStatus.FREE_TRIAL && managed.getTrialEndAt() == null) {
                managed.setTrialEndAt(now.plus(DEFAULT_TRIAL_DAYS, ChronoUnit.DAYS));
            }

            Account saved = accountRepository.save(managed);

            log.info("✅ Provisionamento finalizado | accountId={} status={} trialEndAt={}",
                    saved.getId(),
                    saved.getStatus(),
                    saved.getTrialEndAt());

            return saved;
        });
    }
}