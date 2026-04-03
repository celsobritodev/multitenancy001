package brito.com.multitenancy001.controlplane.signup.app;

import org.flywaydb.core.api.FlywayException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.integration.tenant.TenantProvisioningIntegrationService;
import brito.com.multitenancy001.integration.tenant.TenantSchemaProvisioningIntegrationService;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo provisionamento técnico do tenant durante o onboarding.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar/migrar schema tenant.</li>
 *   <li>Criar tenant owner inicial.</li>
 *   <li>Garantir identidade de login no public schema.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTenantProvisioningService {

    private final TenantSchemaProvisioningIntegrationService tenantSchemaProvisioningIntegrationService;
    private final TenantProvisioningIntegrationService tenantProvisioningIntegrationService;
    private final LoginIdentityService loginIdentityService;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountOnboardingSupport accountOnboardingSupport;

    /**
     * Provisiona schema tenant, owner inicial e login identity.
     *
     * @param account account já criada no public schema
     * @param signupData dados de signup normalizados
     * @return usuário owner do tenant
     */
    public UserSummaryData provisionTenantAndOwner(
            Account account,
            AccountOnboardingSupport.SignupData signupData
    ) {
        String tenantSchema = account.getTenantSchema();

        log.info("Iniciando provisionamento do schema | tenantSchema={}", tenantSchema);

        try {
            tenantSchemaProvisioningIntegrationService.ensureSchemaExistsAndMigrate(tenantSchema);
            log.info("✅ Schema provisionado e migrado | tenantSchema={}", tenantSchema);
        } catch (FlywayException ex) {
            log.error("❌ Falha na migração Flyway | tenantSchema={}", tenantSchema, ex);
            throw accountOnboardingSupport.provisioningFailed(
                    ProvisioningFailureCode.TENANT_MIGRATION_ERROR,
                    ex
            );
        } catch (DataAccessException ex) {
            log.error("❌ Falha na criação do schema | tenantSchema={}", tenantSchema, ex);
            throw accountOnboardingSupport.provisioningFailed(
                    ProvisioningFailureCode.SCHEMA_CREATION_ERROR,
                    ex
            );
        } catch (RuntimeException ex) {
            ProvisioningFailureCode code = (ex instanceof ApiException)
                    ? ProvisioningFailureCode.VALIDATION_ERROR
                    : ProvisioningFailureCode.UNKNOWN;

            log.error("❌ Falha no provisionamento do schema | tenantSchema={}", tenantSchema, ex);
            throw accountOnboardingSupport.provisioningFailed(code, ex);
        }

        UserSummaryData tenantOwner;
        log.info("Criando tenant owner | tenantSchema={} accountId={} email={}",
                tenantSchema,
                account.getId(),
                signupData.loginEmail());

        try {
            tenantOwner = tenantProvisioningIntegrationService.createTenantOwner(
                    tenantSchema,
                    account.getId(),
                    account.getDisplayName(),
                    signupData.loginEmail(),
                    signupData.password()
            );
            log.info("✅ Tenant owner criado | userId={} email={}", tenantOwner.id(), tenantOwner.email());
        } catch (RuntimeException ex) {
            log.error("❌ Falha na criação do tenant owner | tenantSchema={}", tenantSchema, ex);
            throw accountOnboardingSupport.provisioningFailed(
                    ProvisioningFailureCode.TENANT_ADMIN_CREATION_ERROR,
                    ex
            );
        }

        log.info("Garantindo identidade de login no PUBLIC | email={} accountId={}",
                signupData.loginEmail(),
                account.getId());

        try {
            publicSchemaUnitOfWork.tx(() -> {
                loginIdentityService.ensureTenantIdentity(signupData.loginEmail(), account.getId());
                return null;
            });
            log.info("✅ Identidade de login garantida no PUBLIC | email={} accountId={}",
                    signupData.loginEmail(),
                    account.getId());
        } catch (RuntimeException ex) {
            log.error("❌ Falha ao garantir identidade de login | email={} accountId={}",
                    signupData.loginEmail(),
                    account.getId(),
                    ex);
            throw accountOnboardingSupport.provisioningFailed(
                    ProvisioningFailureCode.PUBLIC_PERSISTENCE_ERROR,
                    ex
            );
        }

        return tenantOwner;
    }
}