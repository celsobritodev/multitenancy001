package brito.com.multitenancy001.controlplane.signup.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.audit.AccountProvisioningAuditService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pela auditoria do onboarding de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Registrar início do provisioning.</li>
 *   <li>Registrar sucesso do provisioning.</li>
 *   <li>Registrar falha do provisioning com código e causa.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccountOnboardingAuditService {

    private final AccountProvisioningAuditService accountProvisioningAuditService;
    private final AccountOnboardingSupport accountOnboardingSupport;

    /**
     * Registra auditoria de início do provisioning.
     *
     * @param account account alvo
     * @param signupData dados normalizados
     */
    public void recordStarted(Account account, AccountOnboardingSupport.SignupData signupData) {
        accountProvisioningAuditService.started(
                account.getId(),
                "Provisioning started",
                accountOnboardingSupport.buildDetailsJson(account, signupData, "STARTED", null, null)
        );
    }

    /**
     * Registra auditoria de sucesso do provisioning.
     *
     * @param account account finalizada
     * @param signupData dados normalizados
     */
    public void recordSuccess(Account account, AccountOnboardingSupport.SignupData signupData) {
        accountProvisioningAuditService.success(
                account.getId(),
                "Provisioning success",
                accountOnboardingSupport.buildDetailsJson(account, signupData, "SUCCESS", null, null)
        );
    }

    /**
     * Registra auditoria de falha do provisioning.
     *
     * @param account account alvo
     * @param signupData dados normalizados
     * @param code código de falha
     * @param cause causa técnica/funcional
     */
    public void recordFailure(
            Account account,
            AccountOnboardingSupport.SignupData signupData,
            ProvisioningFailureCode code,
            Throwable cause
    ) {
        accountProvisioningAuditService.failed(
                account.getId(),
                code,
                accountOnboardingSupport.safeMessage(cause),
                accountOnboardingSupport.buildDetailsJson(account, signupData, "FAILED", code, cause)
        );
    }
}