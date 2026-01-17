package brito.com.multitenancy001.controlplane.application;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantProvisioningBridge;
import brito.com.multitenancy001.infrastructure.tenant.TenantUserAdminBridge;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.executor.TxExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {

    private final AccountApiMapper accountApiMapper;
    private final PublicExecutor publicExecutor;
    private final TxExecutor txExecutor;
    private final PublicAccountCreationService publicAccountCreationService;
    private final TenantProvisioningBridge tenantProvisioningBridge;
    private final TenantUserAdminBridge tenantUserAdminBridge;
    private final AccountRepository accountRepository;

    public AccountResponse createAccount(SignupRequest signupRequest) {
        validateSignupRequest(signupRequest);

        log.info("Tentando criar conta");

        Account account = txExecutor.publicTx(() ->
            publicExecutor.run(() -> publicAccountCreationService.createAccountFromSignup(signupRequest))
        );

        tenantProvisioningBridge.ensureSchemaExistsAndMigrate(account.getSchemaName());

        tenantUserAdminBridge.createTenantOwner(
            account.getSchemaName(),
            account.getId(),
            signupRequest.companyEmail(),
            signupRequest.password()
        );

        log.info("✅ Account criada | accountId={} | schemaName={} | slug={}",
                account.getId(), account.getSchemaName(), account.getSlug());

        return accountApiMapper.toResponse(account);
    }

    private void validateSignupRequest(SignupRequest signupRequest) {
        if (!StringUtils.hasText(signupRequest.name())) {
            throw new ApiException("INVALID_COMPANY_NAME", "Nome da empresa é obrigatório", 400);
        }

        if (!StringUtils.hasText(signupRequest.companyEmail())) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        if (!signupRequest.companyEmail().contains("@")) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }

        if (signupRequest.companyDocType() == null) {
            throw new ApiException("INVALID_COMPANY_DOC_TYPE", "Tipo de documento é obrigatório", 400);
        }

        if (!StringUtils.hasText(signupRequest.companyDocNumber())) {
            throw new ApiException("INVALID_COMPANY_DOC_NUMBER", "Número do documento é obrigatório", 400);
        }

        if (!StringUtils.hasText(signupRequest.password()) || !StringUtils.hasText(signupRequest.confirmPassword())) {
            throw new ApiException("INVALID_PASSWORD", "Senha e confirmação são obrigatórias", 400);
        }

        if (!signupRequest.password().equals(signupRequest.confirmPassword())) {
            throw new ApiException("PASSWORD_MISMATCH", "As senhas não coincidem", 400);
        }

        if (accountRepository.existsByCompanyEmailAndDeletedFalse(signupRequest.companyEmail())) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED", "Email já cadastrado na plataforma", 409);
        }

        if (accountRepository.existsByCompanyDocTypeAndCompanyDocNumberAndDeletedFalse(
                signupRequest.companyDocType(), signupRequest.companyDocNumber()
        )) {
            throw new ApiException("DOC_ALREADY_REGISTERED", "Documento já cadastrado na plataforma", 409);
        }
    }
}
