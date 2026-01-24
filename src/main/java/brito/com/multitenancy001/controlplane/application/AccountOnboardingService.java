package brito.com.multitenancy001.controlplane.application;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.TenantAdminResponse;
import brito.com.multitenancy001.controlplane.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaProvisioningFacade;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserProvisioningFacade;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {

    private final AccountApiMapper accountApiMapper;
    private final PublicAccountCreationService publicAccountCreationService;

    private final TenantSchemaProvisioningFacade tenantSchemaProvisioningFacade;
    private final TenantUserProvisioningFacade tenantUserProvisioningFacade;

    private final AccountRepository accountRepository;
    private final PublicUnitOfWork publicUnitOfWork;

    public SignupResponse createAccount(SignupRequest signupRequest) {
        validateSignupRequest(signupRequest);

        log.info("Tentando criar conta");

        Account account = publicUnitOfWork.tx(() ->
                publicAccountCreationService.createAccountFromSignup(signupRequest)
        );

        tenantSchemaProvisioningFacade.ensureSchemaExistsAndMigrate(account.getSchemaName());

        TenantUser tenantOwner = tenantUserProvisioningFacade.createTenantOwner(
                account.getSchemaName(),
                account.getId(),
                account.getDisplayName(),      // ✅ ownerDisplayName
                signupRequest.loginEmail(),
                signupRequest.password()
        );

        log.info("✅ Account criada | accountId={} | schemaName={} | slug={}",
                account.getId(), account.getSchemaName(), account.getSlug());

        AccountResponse accountResponse = accountApiMapper.toResponse(account);

        TenantAdminResponse tenantAdminResponse = new TenantAdminResponse(
                tenantOwner.getId(),
                tenantOwner.getEmail(),
                tenantOwner.getUsername(),
                tenantOwner.getRole()
        );

        return new SignupResponse(accountResponse, tenantAdminResponse);
    }

    private void validateSignupRequest(SignupRequest signupRequest) {

        if (accountRepository.existsByTaxCountryCodeAndTaxIdTypeAndTaxIdNumberAndDeletedFalse(
                "BR", signupRequest.taxIdType(), signupRequest.taxIdNumber()
        )) {
            throw new ApiException("DOC_ALREADY_REGISTERED", "Documento já cadastrado na plataforma", 409);
        }

        if (!StringUtils.hasText(signupRequest.displayName())) {
            throw new ApiException("INVALID_COMPANY_NAME", "Nome da empresa é obrigatório", 400);
        }

        if (!StringUtils.hasText(signupRequest.loginEmail())) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        if (!signupRequest.loginEmail().contains("@")) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }

        if (signupRequest.taxIdType() == null) {
            throw new ApiException("INVALID_COMPANY_DOC_TYPE", "Tipo de documento é obrigatório", 400);
        }

        if (!StringUtils.hasText(signupRequest.taxIdNumber())) {
            throw new ApiException("INVALID_COMPANY_DOC_NUMBER", "Número do documento é obrigatório", 400);
        }

        if (!StringUtils.hasText(signupRequest.password()) || !StringUtils.hasText(signupRequest.confirmPassword())) {
            throw new ApiException("INVALID_PASSWORD", "Senha e confirmação são obrigatórias", 400);
        }

        if (!signupRequest.password().equals(signupRequest.confirmPassword())) {
            throw new ApiException("PASSWORD_MISMATCH", "As senhas não coincidem", 400);
        }

        if (accountRepository.existsByLoginEmailAndDeletedFalse(signupRequest.loginEmail())) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED", "Email já cadastrado na plataforma", 409);
        }

        if (accountRepository.existsByTaxIdTypeAndTaxIdNumberAndDeletedFalse(
                signupRequest.taxIdType(), signupRequest.taxIdNumber()
        )) {
            throw new ApiException("DOC_ALREADY_REGISTERED", "Documento já cadastrado na plataforma", 409);
        }
    }
}
