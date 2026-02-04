package brito.com.multitenancy001.controlplane.signup.app;

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
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.controlplane.signup.app.dto.TenantAdminResult;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaProvisioningFacade;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserProvisioningFacade;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {

    private static final String DEFAULT_TAX_COUNTRY_CODE = "BR";
    private static final long DEFAULT_TRIAL_DAYS = 14L;

    private final TenantSchemaProvisioningFacade tenantSchemaProvisioningFacade;
    private final TenantUserProvisioningFacade tenantUserProvisioningFacade;

    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    private final AccountRepository accountRepository;
    private final PublicUnitOfWork publicUnitOfWork;
    private final AppClock appClock;

    private final AccountProvisioningAuditService provisioningAuditService;

    public SignupResult createAccount(SignupCommand signupCommand) {
        SignupData data = validateAndNormalize(signupCommand);

        log.info("Tentando criar conta | loginEmail={}", data.loginEmail());

        // 1) Cria Account no PUBLIC (TX CP)
        Account account;
        try {
            account = publicUnitOfWork.tx(() -> {
                CreateAccountCommand cmd = new CreateAccountCommand(
                        data.displayName(),
                        data.loginEmail(),
                        data.taxCountryCode(),
                        data.taxIdType(),
                        data.taxIdNumber()
                );

                Account created = AccountFactory.newTenantAccount(cmd);

                // status inicial deve refletir que ainda falta schema+user
                created.setStatus(AccountStatus.PROVISIONING);

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
            try {
                tenantSchemaProvisioningFacade.ensureSchemaExistsAndMigrate(account.getSchemaName());
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
                        account.getSchemaName(),
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
                publicUnitOfWork.tx(() -> {
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

            log.info("✅ Account criada | accountId={} | schemaName={} | slug={} | status={} | trialEndAt={}",
                    finalized.getId(),
                    finalized.getSchemaName(),
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

            log.error("❌ Falha no provisioning | accountId={} | schemaName={} | code={}",
                    account.getId(), account.getSchemaName(), code, wrapped.getCause());

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

            log.error("❌ Falha inesperada no provisioning | accountId={} | schemaName={}",
                    account.getId(), account.getSchemaName(), ex);

            throw ex;
        }
    }

    private Account finalizeProvisioning(Long accountId) {
        return publicUnitOfWork.tx(() -> {
            Account managed = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada após criação", 500));

            Instant now = appClock.instant();

            if (managed.getStatus() == AccountStatus.PROVISIONING) {
                managed.setStatus(AccountStatus.FREE_TRIAL);
            }

            // FREE_TRIAL precisa de trialEndAt (Instant)
            if (managed.getStatus() == AccountStatus.FREE_TRIAL && managed.getTrialEndAt() == null) {
                managed.setTrialEndAt(now.plus(DEFAULT_TRIAL_DAYS, ChronoUnit.DAYS));
            }

            return accountRepository.save(managed);
        });
    }

    private SignupData validateAndNormalize(SignupCommand cmd) {
        if (cmd == null) {
            throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        }

        String displayName = safeTrim(cmd.displayName());
        if (!StringUtils.hasText(displayName)) {
            throw new ApiException("INVALID_COMPANY_NAME", "Nome da empresa é obrigatório", 400);
        }

        String loginEmail = normalizeEmail(cmd.loginEmail());
        if (!StringUtils.hasText(loginEmail)) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }
        if (!looksLikeEmail(loginEmail)) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }

        if (cmd.taxIdType() == null) {
            throw new ApiException("INVALID_COMPANY_DOC_TYPE", "Tipo de documento é obrigatório", 400);
        }

        String taxIdNumber = safeTrim(cmd.taxIdNumber());
        if (!StringUtils.hasText(taxIdNumber)) {
            throw new ApiException("INVALID_COMPANY_DOC_NUMBER", "Número do documento é obrigatório", 400);
        }

        String password = cmd.password();
        String confirmPassword = cmd.confirmPassword();

        if (!StringUtils.hasText(password) || !StringUtils.hasText(confirmPassword)) {
            throw new ApiException("INVALID_PASSWORD", "Senha e confirmação são obrigatórias", 400);
        }
        if (!password.equals(confirmPassword)) {
            throw new ApiException("PASSWORD_MISMATCH", "As senhas não coincidem", 400);
        }

        String taxCountryCode = DEFAULT_TAX_COUNTRY_CODE;

        if (accountRepository.existsByLoginEmailAndDeletedFalse(loginEmail)) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED", "Email já cadastrado na plataforma", 409);
        }

        if (accountRepository.existsByTaxCountryCodeAndTaxIdTypeAndTaxIdNumberAndDeletedFalse(
                taxCountryCode, cmd.taxIdType(), taxIdNumber
        )) {
            throw new ApiException("DOC_ALREADY_REGISTERED", "Documento já cadastrado na plataforma", 409);
        }

        return new SignupData(displayName, loginEmail, taxCountryCode, cmd.taxIdType(), taxIdNumber, password);
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    private static boolean looksLikeEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return false;
        if (at != email.lastIndexOf('@')) return false;
        if (at == email.length() - 1) return false;
        return true;
    }

    private String buildDetailsJson(
            Account account,
            SignupData data,
            String stage,
            ProvisioningFailureCode failureCode,
            Throwable error
    ) {
        String taxIdMasked = maskTaxId(data.taxIdNumber());
        String errorType = (error == null) ? null : error.getClass().getName();
        String errorMsg = (error == null) ? null : safeMessage(error);

        return "{"
                + "\"stage\":\"" + escape(stage) + "\""
                + ",\"accountId\":" + (account == null ? "null" : account.getId())
                + ",\"schemaName\":\"" + escape(account == null ? null : account.getSchemaName()) + "\""
                + ",\"slug\":\"" + escape(account == null ? null : account.getSlug()) + "\""
                + ",\"status\":\"" + escape(account == null ? null : String.valueOf(account.getStatus())) + "\""
                + ",\"displayName\":\"" + escape(data.displayName()) + "\""
                + ",\"loginEmail\":\"" + escape(data.loginEmail()) + "\""
                + ",\"taxCountryCode\":\"" + escape(data.taxCountryCode()) + "\""
                + ",\"taxIdType\":\"" + escape(String.valueOf(data.taxIdType())) + "\""
                + ",\"taxIdMasked\":\"" + escape(taxIdMasked) + "\""
                + ",\"failureCode\":\"" + escape(failureCode == null ? null : failureCode.name()) + "\""
                + ",\"errorType\":\"" + escape(errorType) + "\""
                + ",\"errorMessage\":\"" + escape(errorMsg) + "\""
                + "}";
    }

    private static String maskTaxId(String taxId) {
        if (!StringUtils.hasText(taxId)) return null;
        String digits = taxId.trim();
        if (digits.length() <= 4) return "****";
        return "****" + digits.substring(digits.length() - 4);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return null;
        String msg = t.getMessage();
        if (!StringUtils.hasText(msg)) return t.getClass().getSimpleName();
        return msg;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .trim();
    }

    private static ProvisioningFailedException provisioningFailed(ProvisioningFailureCode code, Throwable cause) {
        return new ProvisioningFailedException(code, cause);
    }

    private record SignupData(
            String displayName,
            String loginEmail,
            String taxCountryCode,
            brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType taxIdType,
            String taxIdNumber,
            String password
    ) {}

    private static class ProvisioningFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final ProvisioningFailureCode code;

        private ProvisioningFailedException(ProvisioningFailureCode code, Throwable cause) {
            super(cause);
            this.code = code == null ? ProvisioningFailureCode.UNKNOWN : code;
        }

        public ProvisioningFailureCode code() {
            return code;
        }
    }
}
