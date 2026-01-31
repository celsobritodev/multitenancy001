package brito.com.multitenancy001.controlplane.signup.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.AccountFactory;
import brito.com.multitenancy001.controlplane.accounts.app.command.CreateAccountCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupRequest;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupResponse;
import brito.com.multitenancy001.controlplane.signup.api.dto.TenantAdminResponse;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaProvisioningFacade;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserProvisioningFacade;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {

    private static final String DEFAULT_TAX_COUNTRY_CODE = "BR";
    private static final long DEFAULT_TRIAL_DAYS = 14L;

    private final AccountApiMapper accountApiMapper;

    private final TenantSchemaProvisioningFacade tenantSchemaProvisioningFacade;
    private final TenantUserProvisioningFacade tenantUserProvisioningFacade;

    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    private final AccountRepository accountRepository;
    private final PublicUnitOfWork publicUnitOfWork;
    private final AppClock appClock;

    public SignupResponse createAccount(SignupRequest signupRequest) {
        SignupData data = validateAndNormalize(signupRequest);

        log.info("Tentando criar conta | loginEmail={}", data.loginEmail());

        Account account = publicUnitOfWork.tx(() -> {
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

        // Provisionamento fora do TX do CP, porque envolve infra/DDL/migrations
        tenantSchemaProvisioningFacade.ensureSchemaExistsAndMigrate(account.getSchemaName());

        UserSummaryData tenantOwner = tenantUserProvisioningFacade.createTenantOwner(
                account.getSchemaName(),
                account.getId(),
                account.getDisplayName(),
                data.loginEmail(),
                data.password()
        );

        // CRÍTICO: registrar identidade de login no PUBLIC para que /api/tenant/auth/login funcione
        publicUnitOfWork.tx(() -> {
            loginIdentityProvisioningService.ensureTenantIdentity(data.loginEmail(), account.getId());
            return null;
        });

        Account finalized = finalizeProvisioning(account.getId());

        log.info("✅ Account criada | accountId={} | schemaName={} | slug={} | status={} | trialEndDate={}",
                finalized.getId(),
                finalized.getSchemaName(),
                finalized.getSlug(),
                finalized.getStatus(),
                finalized.getTrialEndDate()
        );

        AccountResponse accountResponse = accountApiMapper.toResponse(finalized);

        TenantAdminResponse tenantAdminResponse = new TenantAdminResponse(
                tenantOwner.id(),
                tenantOwner.email(),
                tenantOwner.role()
        );

        return new SignupResponse(accountResponse, tenantAdminResponse);
    }

    private Account finalizeProvisioning(Long accountId) {
        return publicUnitOfWork.tx(() -> {
            Account managed = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada após criação", 500));

            LocalDateTime now = appClock.now();

            // Se ainda está provisionando, entra em FREE_TRIAL
            if (managed.getStatus() == AccountStatus.PROVISIONING) {
                managed.setStatus(AccountStatus.FREE_TRIAL);
            }

            // Garantia de consistência: FREE_TRIAL precisa de trialEndDate
            if (managed.getStatus() == AccountStatus.FREE_TRIAL && managed.getTrialEndDate() == null) {
                managed.setTrialEndDate(now.plusDays(DEFAULT_TRIAL_DAYS));
            }

            return accountRepository.save(managed);
        });
    }

    private SignupData validateAndNormalize(SignupRequest req) {
        if (req == null) {
            throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        }

        String displayName = safeTrim(req.displayName());
        if (!StringUtils.hasText(displayName)) {
            throw new ApiException("INVALID_COMPANY_NAME", "Nome da empresa é obrigatório", 400);
        }

        String loginEmail = normalizeEmail(req.loginEmail());
        if (!StringUtils.hasText(loginEmail)) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }
        if (!looksLikeEmail(loginEmail)) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }

        if (req.taxIdType() == null) {
            throw new ApiException("INVALID_COMPANY_DOC_TYPE", "Tipo de documento é obrigatório", 400);
        }

        String taxIdNumber = safeTrim(req.taxIdNumber());
        if (!StringUtils.hasText(taxIdNumber)) {
            throw new ApiException("INVALID_COMPANY_DOC_NUMBER", "Número do documento é obrigatório", 400);
        }

        String password = req.password();
        String confirmPassword = req.confirmPassword();

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
                taxCountryCode, req.taxIdType(), taxIdNumber
        )) {
            throw new ApiException("DOC_ALREADY_REGISTERED", "Documento já cadastrado na plataforma", 409);
        }

        return new SignupData(displayName, loginEmail, taxCountryCode, req.taxIdType(), taxIdNumber, password);
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

    private record SignupData(
            String displayName,
            String loginEmail,
            String taxCountryCode,
            brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType taxIdType,
            String taxIdNumber,
            String password
    ) {}
}
