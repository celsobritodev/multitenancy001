package brito.com.multitenancy001.controlplane.application;

import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.exec.PublicExecutor;
import brito.com.multitenancy001.infrastructure.exec.TenantExecutor;
import brito.com.multitenancy001.infrastructure.exec.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.application.provisioning.TenantSchemaProvisioningService;
import brito.com.multitenancy001.tenant.application.username.generator.UsernameGeneratorService;
import brito.com.multitenancy001.tenant.domain.security.TenantRole;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {
	
	private final UsernameGeneratorService usernameGenerator;

	
	private final AccountApiMapper accountApiMapper;
	
	private final PublicExecutor publicExecutor;
	private final TxExecutor txExecutor;
	private final TenantExecutor tenantExecutor;

	
	
	private final PublicAccountService publicAccountService;
	  private final TenantSchemaProvisioningService tenantSchemaProvisioningService;
	  private final TenantUserRepository tenantUserRepository;
	  private final PasswordEncoder passwordEncoder;
	   private final AccountRepository accountRepository;
	  

    /* =========================================================
       1. CRIAÇÃO DE CONTA 
       ========================================================= */

	   public AccountResponse createAccount(SignupRequest request) {
		    validateSignupRequest(request);
		    
		    log.info("Tentando criar conta");

		    Account account = txExecutor.publicTx(() ->
		        publicExecutor.run(() -> publicAccountService.createAccountFromSignup(request))
		    );

		    tenantSchemaProvisioningService.ensureSchemaExistsAndMigrate(account.getSchemaName());

		    createTenantOwnerInTenant(account, request);

		    log.info("✅ Account criada | accountId={} | schema={} | slug={}",
		            account.getId(), account.getSchemaName(), account.getSlug());

		    return accountApiMapper.toResponse(account);
		}

    

protected TenantUser createTenantOwnerInTenant(Account account, SignupRequest request) {
    return tenantExecutor.run(account.getSchemaName(), () ->
        txExecutor.tenantTx(() -> {

            boolean emailExists = tenantUserRepository
                    .existsByEmailAndAccountId(request.companyEmail(), account.getId());

            if (emailExists) {
                throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
            }

            TenantUser u = new TenantUser();
            u.setAccountId(account.getId());
            u.setName("Administrador");
            u.setEmail(request.companyEmail());
            u.setPassword(passwordEncoder.encode(request.password()));
            u.setRole(TenantRole.TENANT_ACCOUNT_OWNER);
            u.setSuspendedByAccount(false);
            u.setSuspendedByAdmin(false);
            u.setCreatedAt(LocalDateTime.now());
            u.setTimezone("America/Sao_Paulo");
            u.setLocale("pt_BR");

            for (int attempt = 0; attempt < 5; attempt++) {
                u.setUsername(usernameGenerator.generateFromEmail(request.companyEmail(), account.getId()));

                try {
                    return tenantUserRepository.save(u);
                } catch (DataIntegrityViolationException e) {
                    log.warn("Colisão de username ao criar admin. Tentativa {}. accountId={} email={}",
                            attempt + 1, account.getId(), request.companyEmail());
                }
            }

            throw new IllegalStateException("Failed to create tenant admin due to repeated username collisions");
        })
    );
}
  
    
  
  
   
   
    
    private void validateSignupRequest(SignupRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw new ApiException("INVALID_COMPANY_NAME", "Nome da empresa é obrigatório", 400);
        }
        
        if (!StringUtils.hasText(request.companyEmail())) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        if (!request.companyEmail().contains("@")) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }

        // ✅ docType + docNumber
        if (request.companyDocType() == null) {
            throw new ApiException("INVALID_COMPANY_DOC_TYPE", "Tipo de documento é obrigatório", 400);
        }

        if (!StringUtils.hasText(request.companyDocNumber())) {
            throw new ApiException("INVALID_COMPANY_DOC_NUMBER", "Número do documento é obrigatório", 400);
        }

        if (!StringUtils.hasText(request.password()) || !StringUtils.hasText(request.confirmPassword())) {
            throw new ApiException("INVALID_PASSWORD", "Senha e confirmação são obrigatórias", 400);
        }

        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException("PASSWORD_MISMATCH", "As senhas não coincidem", 400);
        }

        // ✅ ajuste para método que faz sentido com seus campos atuais
        if (accountRepository.existsByCompanyEmailAndDeletedFalse(request.companyEmail())) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED",
                    "Email já cadastrado na plataforma", 409);
        }

        // ✅ recomendável também bloquear duplicidade de docNumber
        if (accountRepository.existsByCompanyDocTypeAndCompanyDocNumberAndDeletedFalse(
                request.companyDocType(), request.companyDocNumber()
        )) {
            throw new ApiException("DOC_ALREADY_REGISTERED",
                    "Documento já cadastrado na plataforma", 409);
        }
        
    }



	

	

    
}

