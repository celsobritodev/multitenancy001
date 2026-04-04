package brito.com.multitenancy001.controlplane.signup.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.controlplane.signup.app.dto.TenantAdminResult;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando responsável por coordenar o onboarding completo de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar e normalizar o comando de signup.</li>
 *   <li>Criar a Account no public schema.</li>
 *   <li>Acionar provisionamento de tenant e usuário owner.</li>
 *   <li>Finalizar provisioning no public schema.</li>
 *   <li>Registrar auditoria de início, sucesso e falha.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOnboardingCommandService {

    private final AccountOnboardingValidator accountOnboardingValidator;
    private final AccountProvisioningLifecycleService accountProvisioningLifecycleService;
    private final AccountTenantProvisioningService accountTenantProvisioningService;
    private final AccountOnboardingAuditService accountOnboardingAuditService;
    private final AccountOnboardingHelper accountOnboardingHelper;

    /**
     * Executa o fluxo de criação e provisionamento de uma Account.
     *
     * @param signupCommand comando de signup
     * @return resultado consolidado do onboarding
     */
    public SignupResult createAccount(SignupCommand signupCommand) {
        AccountOnboardingHelper.SignupData signupData =
                accountOnboardingValidator.validateAndNormalize(signupCommand);

        log.info("🚀 Tentando criar conta | loginEmail={} | displayName={}",
                signupData.loginEmail(),
                signupData.displayName());

        Account account;
        try {
            account = accountProvisioningLifecycleService.createProvisioningAccount(signupData);
            log.info("✅ Account criada no PUBLIC | accountId={} tenantSchema={}",
                    account.getId(),
                    account.getTenantSchema());
        } catch (RuntimeException ex) {
            log.error("❌ Falha criando Account no PUBLIC | loginEmail={}",
                    signupData.loginEmail(),
                    ex);
            throw ex;
        }

        accountOnboardingAuditService.recordStarted(account, signupData);

        UserSummaryData tenantOwner = null;

        try {
            tenantOwner = accountTenantProvisioningService.provisionTenantAndOwner(account, signupData);

            Account finalizedAccount = accountProvisioningLifecycleService.finalizeProvisioning(account.getId());

            accountOnboardingAuditService.recordSuccess(finalizedAccount, signupData);

            log.info("✅ Account criada com sucesso | accountId={} | tenantSchema={} | slug={} | status={} | trialEndAt={}",
                    finalizedAccount.getId(),
                    finalizedAccount.getTenantSchema(),
                    finalizedAccount.getSlug(),
                    finalizedAccount.getStatus(),
                    finalizedAccount.getTrialEndAt());

            TenantAdminResult tenantAdminResult = new TenantAdminResult(
                    tenantOwner.id(),
                    tenantOwner.email(),
                    tenantOwner.role()
            );

            return new SignupResult(finalizedAccount, tenantAdminResult);

        } catch (AccountOnboardingHelper.ProvisioningFailedException ex) {
            ProvisioningFailureCode code = ex.code();
            String message = accountOnboardingHelper.safeMessage(ex.getCause());

            log.error("❌ Falha no provisioning | accountId={} | tenantSchema={} | code={} | message={}",
                    account.getId(),
                    account.getTenantSchema(),
                    code,
                    message,
                    ex.getCause());

            accountOnboardingAuditService.recordFailure(
                    account,
                    signupData,
                    code,
                    ex.getCause()
            );

            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw ex;

        } catch (RuntimeException ex) {
            log.error("❌ Falha inesperada no provisioning | accountId={} | tenantSchema={}",
                    account.getId(),
                    account.getTenantSchema(),
                    ex);

            accountOnboardingAuditService.recordFailure(
                    account,
                    signupData,
                    ProvisioningFailureCode.UNKNOWN,
                    ex
            );

            throw ex;
        }
    }
}