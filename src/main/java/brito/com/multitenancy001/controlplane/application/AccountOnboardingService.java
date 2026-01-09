package brito.com.multitenancy001.controlplane.application;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infra.exec.PublicExecutor;
import brito.com.multitenancy001.infra.exec.TenantExecutor;
import brito.com.multitenancy001.infra.exec.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.application.provisioning.TenantSchemaProvisioningService;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOnboardingService {
	
	  
	private final PublicExecutor publicExec;
	private final TxExecutor tx;
	private final TenantExecutor tenantExec;

	
	
	private final PublicAccountService publicAccountService;
	  private final TenantSchemaProvisioningService tenantSchemaService;
	  private final TenantUserRepository tenantUserRepository;
	  private final PasswordEncoder passwordEncoder;
	   private final AccountRepository accountRepository;
	  

    /* =========================================================
       1. CRIAÇÃO DE CONTA 
       ========================================================= */

	   public AccountResponse createAccount(SignupRequest request) {
		    validateSignupRequest(request);

		    Account account = tx.publicTx(() ->
		        publicExec.run(() -> publicAccountService.createAccountFromSignup(request))
		    );

		    tenantSchemaService.schemaMigrationService(account.getSchemaName());

		    createTenantAdminInTenant(account, request);

		    log.info("✅ Account criada | accountId={} | schema={} | slug={}",
		            account.getId(), account.getSchemaName(), account.getSlug());

		    return AccountResponse.fromEntity(account);
		}

    
   protected TenantUser createTenantAdminInTenant(Account account, SignupRequest request) {
    return tenantExec.run(account.getSchemaName(), () ->
        tx.tenantTx(() -> {
            String username = generateUsernameFromEmail(request.companyEmail());

            boolean usernameExists = tenantUserRepository.existsByUsernameAndAccountId(username, account.getId());
            boolean emailExists = tenantUserRepository.existsByEmailAndAccountId(request.companyEmail(), account.getId());

            if (usernameExists) {
                username = ensureUniqueUsername(username, account.getId());
            }
            if (emailExists) {
                throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
            }

            TenantUser u = new TenantUser();
            u.setAccountId(account.getId());
            u.setName("Administrador");
            u.setUsername(username);
            u.setEmail(request.companyEmail());
            u.setPassword(passwordEncoder.encode(request.password()));
            u.setRole(TenantRole.TENANT_ADMIN);
            u.setSuspendedByAccount(false);
            u.setSuspendedByAdmin(false);
            u.setCreatedAt(LocalDateTime.now());
            u.setTimezone("America/Sao_Paulo");
            u.setLocale("pt_BR");

            return tenantUserRepository.save(u);
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


    

    private String generateUsernameFromEmail(String email) {
        String base = email.split("@")[0].toLowerCase();
        return base.replaceAll("[^a-z0-9._-]", "_")
                   .replaceAll("_{2,}", "_")
                   .replaceAll("^_|_$", "");
    }
    
    


    private String ensureUniqueUsername(String baseUsername, Long accountId) {
        String username = baseUsername;
        int counter = 1;
        
        while (tenantUserRepository.existsByUsernameAndAccountId(username, accountId)) {
            username = baseUsername + counter;
            counter++;
        }
        
        return username;
    }

    
}

