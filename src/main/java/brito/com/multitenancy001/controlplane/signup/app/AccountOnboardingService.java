package brito.com.multitenancy001.controlplane.signup.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.flywaydb.core.api.FlywayException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.app.AccountFactory;
import brito.com.multitenancy001.controlplane.accounts.app.audit.AccountProvisioningAuditService;
import brito.com.multitenancy001.controlplane.accounts.app.command.CreateAccountCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.controlplane.signup.app.dto.TenantAdminResult;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaProvisioningService;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.provisioning.app.TenantUserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {

    private static final String DEFAULT_TAX_COUNTRY_CODE = "BR";
    private static final long DEFAULT_TRIAL_DAYS = 14L;

    private final TenantSchemaProvisioningService tenantSchemaProvisioningFacade;
    private final TenantUserProvisioningService tenantUserProvisioningFacade;

    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    private final AccountRepository accountRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;

    private final AccountProvisioningAuditService provisioningAuditService;

    public SignupResult createAccount(SignupCommand signupCommand) {
        SignupData data = validateAndNormalize(signupCommand);

        log.info("Tentando criar conta | loginEmail={}", data.loginEmail());

        // 1) Cria Account no PUBLIC (TX CP)
        Account account;
        try {
            account = publicSchemaUnitOfWork.tx(() -> {
                CreateAccountCommand cmd = new CreateAccountCommand(
                        data.displayName(),
                        data.loginEmail(),
                        data.taxCountryCode(),
                        data.taxIdType(),
                        data.taxIdNumber()
                );

                Account created = AccountFactory.newTenantAccount(cmd);

                created.setStatus(AccountStatus.PROVISIONING);

                // ✅ se a factory não setar tenantSchema, garante aqui
                created.ensureTenantSchema();

                return accountRepository.save(created);
            });
        } catch (RuntimeException ex) {
            log.error("❌ Falha criando Account no PUBLIC | loginEmail={}", data.loginEmail(), ex);
            throw ex;
        }

        // 2) Auditoria STARTED
        provisioningAuditService.started(
                account.getId(),
                "Provisioning started",
                buildDetailsJson(account, data, "STARTED", null, null)
        );

        UserSummaryData tenantOwner = null;

        try {
            // 3) Provisionamento fora do TX do CP (envolve infra/DDL/migrations)
            String tenantSchema = account.getTenantSchema();

            try {
                tenantSchemaProvisioningFacade.ensureSchemaExistsAndMigrate(tenantSchema);
            } catch (FlywayException ex) {
                throw provisioningFailed(ProvisioningFailureCode.TENANT_MIGRATION_ERROR, ex);
            } catch (DataAccessException ex) {
                throw provisioningFailed(ProvisioningFailureCode.SCHEMA_CREATION_ERROR, ex);
            } catch (RuntimeException ex) {
                ProvisioningFailureCode code = (ex instanceof ApiException)
                        ? ProvisioningFailureCode.VALIDATION_ERROR
                        : ProvisioningFailureCode.UNKNOWN;
                throw provisioningFailed(code, ex);
            }

            // 4) Criação do tenant owner
            try {
                tenantOwner = tenantUserProvisioningFacade.createTenantOwner(
                        tenantSchema,
                        account.getId(),
                        account.getDisplayName(),
                        data.loginEmail(),
                        data.password()
                );
            } catch (RuntimeException ex) {
                throw provisioningFailed(ProvisioningFailureCode.TENANT_ADMIN_CREATION_ERROR, ex);
            }

            // 5) Registrar identidade de login no PUBLIC (para /api/tenant/auth/login)
            try {
                publicSchemaUnitOfWork.tx(() -> {
                    loginIdentityProvisioningService.ensureTenantIdentity(data.loginEmail(), account.getId());
                    return null;
                });
            } catch (RuntimeException ex) {
                throw provisioningFailed(ProvisioningFailureCode.PUBLIC_PERSISTENCE_ERROR, ex);
            }

            // 6) Finaliza status/trial no PUBLIC
            Account finalized;
            try {
                finalized = finalizeProvisioning(account.getId());
            } catch (RuntimeException ex) {
                throw provisioningFailed(ProvisioningFailureCode.PUBLIC_PERSISTENCE_ERROR, ex);
            }

            // 7) Auditoria SUCCESS
            provisioningAuditService.success(
                    finalized.getId(),
                    "Provisioning success",
                    buildDetailsJson(finalized, data, "SUCCESS", null, null)
            );

            log.info("✅ Account criada | accountId={} | tenantSchema={} | slug={} | status={} | trialEndAt={}",
                    finalized.getId(),
                    finalized.getTenantSchema(),
                    finalized.getSlug(),
                    finalized.getStatus(),
                    finalized.getTrialEndAt()
            );

            TenantAdminResult tenantAdminResult = new TenantAdminResult(
                    tenantOwner.id(),
                    tenantOwner.email(),
                    tenantOwner.role()
            );

            return new SignupResult(finalized, tenantAdminResult);

        } catch (ProvisioningFailedException wrapped) {
            ProvisioningFailureCode code = wrapped.code();

            String message = safeMessage(wrapped.getCause());
            provisioningAuditService.failed(
                    account.getId(),
                    code,
                    message,
                    buildDetailsJson(account, data, "FAILED", code, wrapped.getCause())
            );

            log.error("❌ Falha no provisioning | accountId={} | tenantSchema={} | code={}",
                    account.getId(), account.getTenantSchema(), code, wrapped.getCause());

            if (wrapped.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw wrapped;

        } catch (RuntimeException ex) {
            provisioningAuditService.failed(
                    account.getId(),
                    ProvisioningFailureCode.UNKNOWN,
                    safeMessage(ex),
                    buildDetailsJson(account, data, "FAILED", ProvisioningFailureCode.UNKNOWN, ex)
            );

            log.error("❌ Falha inesperada no provisioning | accountId={} | tenantSchema={}",
                    account.getId(), account.getTenantSchema(), ex);

            throw ex;
        }
    }

    private Account finalizeProvisioning(Long accountId) {
        return publicSchemaUnitOfWork.tx(() -> {
            Account managed = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada após criação", 500));

            Instant now = appClock.instant();

            if (managed.getStatus() == AccountStatus.PROVISIONING) {
                managed.setStatus(AccountStatus.FREE_TRIAL);
            }

            if (managed.getStatus() == AccountStatus.FREE_TRIAL && managed.getTrialEndAt() == null) {
                managed.setTrialEndAt(now.plus(DEFAULT_TRIAL_DAYS, ChronoUnit.DAYS));
            }

            return accountRepository.save(managed);
        });
    }

    private SignupData validateAndNormalize(SignupCommand cmd) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Requisição inválida", 400);
        }

        String displayName = safeTrim(cmd.displayName());
        if (!StringUtils.hasText(displayName)) {
            throw new ApiException(ApiErrorCode.INVALID_COMPANY_NAME, "Nome da empresa é obrigatório", 400);
        }

        String loginEmail = EmailNormalizer.normalizeOrNull(cmd.loginEmail());
        if (!StringUtils.hasText(loginEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", 400);
        }
        if (!looksLikeEmail(loginEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", 400);
        }

        TaxIdType taxIdType = cmd.taxIdType();
        if (taxIdType == null) {
            throw new ApiException(ApiErrorCode.INVALID_COMPANY_DOC_TYPE, "Tipo de documento é obrigatório", 400);
        }

        String taxIdNumber = safeTrim(cmd.taxIdNumber());
        if (!StringUtils.hasText(taxIdNumber)) {
            throw new ApiException(ApiErrorCode.INVALID_COMPANY_DOC_NUMBER, "Número do documento é obrigatório", 400);
        }

        String password = safeTrim(cmd.password());
        if (!StringUtils.hasText(password)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);
        }

        String confirmPassword = safeTrim(cmd.confirmPassword());
        if (!StringUtils.hasText(confirmPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_CONFIRM_PASSWORD, "Confirmação de senha é obrigatória", 400);
        }

        if (!password.equals(confirmPassword)) {
            throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Senha e confirmação não conferem", 400);
        }

        String taxCountryCode = DEFAULT_TAX_COUNTRY_CODE;

        return new SignupData(
                displayName,
                loginEmail,
                taxCountryCode,
                taxIdType,
                taxIdNumber,
                password
        );
    }

    private String safeTrim(String s) {
        return (s == null ? null : s.trim());
    }

    private boolean looksLikeEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    private ProvisioningFailedException provisioningFailed(ProvisioningFailureCode code, Exception ex) {
        return new ProvisioningFailedException(code, ex);
    }

    private String safeMessage(Throwable t) {
        if (t == null) return null;
        String m = t.getMessage();
        return (m == null ? t.getClass().getSimpleName() : m);
    }

    private String buildDetailsJson(
            Account account,
            SignupData data,
            String stage,
            ProvisioningFailureCode code,
            Throwable cause
    ) {
        String accountId = (account == null || account.getId() == null) ? null : String.valueOf(account.getId());
        String tenantSchema = (account == null ? null : account.getTenantSchema());
        String slug = (account == null ? null : account.getSlug());

        String causeClass = (cause == null ? null : cause.getClass().getName());
        String causeMessage = (cause == null ? null : safeMessage(cause));

        return "{"
                + "\"stage\":\"" + stage + "\""
                + ",\"accountId\":\"" + accountId + "\""
                + ",\"tenantSchema\":\"" + tenantSchema + "\""
                + ",\"slug\":\"" + slug + "\""
                + ",\"loginEmail\":\"" + (data == null ? null : data.loginEmail()) + "\""
                + ",\"code\":\"" + (code == null ? null : code.name()) + "\""
                + ",\"causeClass\":\"" + causeClass + "\""
                + ",\"causeMessage\":\"" + causeMessage + "\""
                + "}";
    }

    private record SignupData(
            String displayName,
            String loginEmail,
            String taxCountryCode,
            TaxIdType taxIdType,
            String taxIdNumber,
            String password
    ) {}

    private static class ProvisioningFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final ProvisioningFailureCode code;

        public ProvisioningFailedException(ProvisioningFailureCode code, Throwable cause) {
            super(cause);
            this.code = code;
        }

        public ProvisioningFailureCode code() {
            return code;
        }
    }
}
