package brito.com.multitenancy001.controlplane.signup.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.audit.AccountProvisioningAuditService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pela auditoria do onboarding de account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Registrar início do provisioning.</li>
 *   <li>Registrar sucesso do provisioning.</li>
 *   <li>Registrar falha com details estruturados.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOnboardingAuditService {

    private final AccountProvisioningAuditService accountProvisioningAuditService;
    private final AccountOnboardingSupport accountOnboardingSupport;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra início do onboarding.
     *
     * @param account account criada em provisioning
     * @param signupData dados normalizados
     */
    public void recordStarted(
            Account account,
            AccountOnboardingSupport.SignupData signupData
    ) {
        accountProvisioningAuditService.started(
                account.getId(),
                "Provisioning started",
                jsonDetailsMapper.toJson(
                        accountOnboardingSupport.buildDetails(
                                account,
                                signupData,
                                "STARTED",
                                null,
                                null
                        )
                )
        );

        log.debug("Auditoria STARTED registrada | accountId={} | tenantSchema={}",
                account.getId(),
                account.getTenantSchema());
    }

    /**
     * Registra sucesso do onboarding.
     *
     * @param account account finalizada
     * @param signupData dados normalizados
     */
    public void recordSuccess(
            Account account,
            AccountOnboardingSupport.SignupData signupData
    ) {
        accountProvisioningAuditService.success(
                account.getId(),
                "Provisioning success",
                jsonDetailsMapper.toJson(
                        accountOnboardingSupport.buildDetails(
                                account,
                                signupData,
                                "SUCCESS",
                                null,
                                null
                        )
                )
        );

        log.debug("Auditoria SUCCESS registrada | accountId={} | tenantSchema={}",
                account.getId(),
                account.getTenantSchema());
    }

    /**
     * Registra falha do onboarding.
     *
     * @param account account alvo
     * @param signupData dados normalizados
     * @param code código da falha
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
                jsonDetailsMapper.toJson(
                        accountOnboardingSupport.buildDetails(
                                account,
                                signupData,
                                "FAILED",
                                code,
                                cause
                        )
                )
        );

        log.debug("Auditoria FAILED registrada | accountId={} | tenantSchema={} | code={}",
                account.getId(),
                account.getTenantSchema(),
                code);
    }
}