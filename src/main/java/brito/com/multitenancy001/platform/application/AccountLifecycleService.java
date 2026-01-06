package brito.com.multitenancy001.platform.application;

import brito.com.multitenancy001.infrastructure.multitenancy.SchemaContext;
import brito.com.multitenancy001.platform.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.platform.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.platform.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.platform.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.platform.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;
import brito.com.multitenancy001.platform.persistence.publicdb.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.application.TenantSchemaProvisioningService;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
import brito.com.multitenancy001.tenant.model.TenantUser;
import brito.com.multitenancy001.tenant.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j

public class AccountLifecycleService {

    private final AccountRepository accountRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantSchemaProvisioningService tenantSchemaService;
    private final PasswordEncoder passwordEncoder;
    private final PublicAccountService publicAccountService;


    /* =========================================================
       1. CRIAÇÃO DE CONTA (SIGNUP SIMPLIFICADO)
       ========================================================= */

    public AccountResponse createAccount(SignupRequest request) {
        validateSignupRequest(request);

        SchemaContext.unbindSchema(); // garante PUBLIC

        // A) cria no PUBLIC e COMMITA (transação do publicAccountService)
        TenantAccount account = publicAccountService.createAccountFromSignup(request);

        // B) cria schema + roda flyway do tenant (sem transação JPA public aberta)
        tenantSchemaService.ensureSchemaAndMigrate(account.getSchemaName());

        // C) cria admin no TENANT com tenantTransactionManager
        createTenantAdminInTenant(account, request);

        log.info("✅ Account criada | accountId={} | schema={} | slug={}",
                account.getId(), account.getSchemaName(), account.getSlug());

        return AccountResponse.fromEntity(account);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUser createTenantAdminInTenant(TenantAccount account, SignupRequest request) {
        SchemaContext.bindSchema(account.getSchemaName());
        try {
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

        } finally {
            SchemaContext.unbindSchema();
        }
    }

    

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccounts() {
        SchemaContext.unbindSchema();
        return accountRepository.findAllByDeletedFalse()
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByIdWithAdmin(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
        return AccountResponse.fromEntity(account);
    }

    @Transactional(readOnly = true)
    public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

       

        return AccountAdminDetailsResponse.from(account, null);


    }

    /* =========================================================
       3. GERENCIAMENTO DE STATUS DE CONTA
       ========================================================= */

  public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
    SchemaContext.unbindSchema();

    TenantAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

    if (account.isSystemAccount()) {
        throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
                "Contas do sistema não podem ter seu status alterado", 403);
    }

    TenantAccountStatus previous = account.getStatus();
    TenantAccountStatus next = req.status();

    if (previous == next) {
        return buildStatusChangeResponse(account, previous, false, "NONE", 0);
    }

    if (previous == TenantAccountStatus.CANCELLED) {
        throw new ApiException("ACCOUNT_CANCELLED",
                "Conta cancelada não pode ter status alterado", 409);
    }

    if (next == TenantAccountStatus.FREE_TRIAL && previous != TenantAccountStatus.FREE_TRIAL) {
        throw new ApiException("INVALID_STATUS_TRANSITION",
                "Não é permitido voltar para FREE_TRIAL", 409);
    }

    // 1) grava em PUBLIC
    account.setStatus(next);
    if (next == TenantAccountStatus.ACTIVE) account.setDeletedAt(null);
    accountRepository.save(account);

    // 2) side-effects no tenant
    int affected = 0;
    boolean applied = false;
    String action = "NONE";

    if (next == TenantAccountStatus.SUSPENDED) {
        affected = suspendTenantUsersByAccount(account);
        applied = true;
        action = "SUSPEND_BY_ACCOUNT";
    } else if (next == TenantAccountStatus.ACTIVE) {
        // ✅ reativar todos por conta, mantendo suspensosByAdmin
        affected = unsuspendTenantUsersByAccount(account);
        applied = true;
        action = "UNSUSPEND_BY_ACCOUNT";
    } else if (next == TenantAccountStatus.CANCELLED) {
        affected = cancelAccount(account);
        applied = true;
        action = "CANCELLED";
    }

    return buildStatusChangeResponse(account, previous, applied, action, affected);
}
  
 
  

   
   @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
   protected int suspendTenantUsersByAccount(TenantAccount account) {
       String schema = account.getSchemaName();

       if ("public".equals(schema)) return 0;
       if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
       if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

       SchemaContext.bindSchema(schema);
       try {
           // ✅ UPDATE em massa: suspendedByAccount = true, preserva suspendedByAdmin
           return tenantUserRepository.suspendAllByAccount(account.getId());
       } finally {
           SchemaContext.unbindSchema();
       }
   }

   @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
   protected int unsuspendTenantUsersByAccount(TenantAccount account) {
       String schema = account.getSchemaName();

       if ("public".equals(schema)) return 0;
       if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
       if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

       SchemaContext.bindSchema(schema);
       try {
           // ✅ UPDATE em massa: suspendedByAccount = false, preserva suspendedByAdmin
           return tenantUserRepository.unsuspendAllByAccount(account.getId());
       } finally {
           SchemaContext.unbindSchema();
       }
   }


    
    
    /* =========================================================
       4. SOFT DELETE / RESTORE
       ========================================================= */

    @Transactional
    public void softDeleteAccount(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = getAccountById(accountId);

        if (account.isSystemAccount()) {
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", 
                    "Não é permitido excluir contas do sistema", 403);
        }

        softDeleteAccountTx(accountId);
        softDeleteTenantUsersTx(accountId);
    }

    @Transactional
    protected void softDeleteAccountTx(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = getAccountById(accountId);
        account.softDelete();
        accountRepository.save(account);
    }

    public void restoreAccount(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = getAccountById(accountId);

        if (account.isSystemAccount() && account.isDeleted()) {
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
                    "Contas do sistema não podem ser restauradas via este endpoint", 403);
        }

        restoreAccountTx(accountId);
        restoreTenantUsersTx(accountId);
    }

    @Transactional
    protected void restoreAccountTx(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = getAccountById(accountId);
        account.restore();
        accountRepository.save(account);
    }

    /* =========================================================
       5. LISTAGEM DE USUÁRIOS DO TENANT (ADMIN)
       ========================================================= */

    public List<TenantUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyActive) {
        // resolve conta em PUBLIC
        SchemaContext.unbindSchema();
        TenantAccount account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        String schema = account.getSchemaName();

        if (!tenantSchemaService.validateTenantSchema(schema)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
        }
        if (!tenantSchemaService.tableExists(schema, "users_tenant")) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela users_tenant não existe no tenant", 404);
        }

        SchemaContext.bindSchema(schema);
        try {
            return listTenantUsersTx(account.getId(), onlyActive);
        } finally {
            SchemaContext.unbindSchema();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    protected List<TenantUserSummaryResponse> listTenantUsersTx(Long accountId, boolean onlyActive) {
    	 List<TenantUser> users;
    	    
    	    if (onlyActive) {
    	        // Use o novo método customizado
    	        users = tenantUserRepository.findActiveUsersByAccount(accountId);
    	    } else {
    	        users = tenantUserRepository.findByAccountId(accountId);
    	    }
        return users.stream().map(TenantUserSummaryResponse::from).toList();
    }

    /* =========================================================
       MÉTODOS AUXILIARES (PRIVADOS)
       ========================================================= */

    
    
    
    
    
   @Transactional(transactionManager = "tenantTransactionManager")
    protected TenantUser createTenantAdminFromSignup(Long accountId, SignupRequest request) {
        String username = generateUsernameFromEmail(request.companyEmail());
        
        boolean usernameExists = tenantUserRepository.existsByUsernameAndAccountId(username, accountId);
        boolean emailExists = tenantUserRepository.existsByEmailAndAccountId(request.companyEmail(), accountId);

        if (usernameExists) {
            username = ensureUniqueUsername(username, accountId);
        }
        
        if (emailExists) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
        }

        TenantUser u = new TenantUser();
        u.setAccountId(accountId);
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
    }

   private AccountStatusChangeResponse buildStatusChangeResponse(
	        TenantAccount account,
	        TenantAccountStatus previous,
	        boolean tenantUsersUpdated,
	        String action,
	        int count
	) {
	    return new AccountStatusChangeResponse(
	            account.getId(),
	            account.getStatus().name(),
	            previous.name(),
	            LocalDateTime.now(),
	            account.getSchemaName(),
	            new AccountStatusChangeResponse.SideEffects(tenantUsersUpdated, action, count)
	    );
	}


    public int cancelAccount(TenantAccount account) {
        // 1) PUBLIC
        cancelAccountPublicTx(account);

        // 2) TENANT (se existir)
        String schema = account.getSchemaName();
        boolean schemaOk = tenantSchemaService.validateTenantSchema(schema);
        boolean tableOk = schemaOk && tenantSchemaService.tableExists(schema, "users_tenant");
        if (!tableOk) return 0;

        SchemaContext.bindSchema(schema);
        try {
            return cancelAccountTenantTx(account);
        } finally {
            SchemaContext.unbindSchema();
        }
    }

    @Transactional(transactionManager = "publicTransactionManager", propagation = Propagation.REQUIRES_NEW)
    protected void cancelAccountPublicTx(TenantAccount account) {
        SchemaContext.unbindSchema();
        account.setDeletedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    protected int cancelAccountTenantTx(TenantAccount account) {
        List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
        users.forEach(TenantUser::softDelete);
        tenantUserRepository.saveAll(users);
        return users.size();
    }


    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public int cancelAccountTx(TenantAccount account) {
        List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
        users.forEach(TenantUser::softDelete);
        tenantUserRepository.saveAll(users);
        return users.size();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public int suspendTenantUsersTx(TenantAccount account) {
        String schema = account.getSchemaName();

        if ("public".equals(schema)) return 0;
        if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
        if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

        SchemaContext.bindSchema(schema);
        try {
            List<TenantUser> users = tenantUserRepository.findByAccountIdAndDeletedFalse(account.getId());

            // ✅ conta suspensa => todos marcados como suspensos por conta
            users.forEach(u -> u.setSuspendedByAccount(true));

            tenantUserRepository.saveAll(users);
            return users.size();
        } finally {
            SchemaContext.unbindSchema();
        }
    }
    
    
    @Transactional(transactionManager = "tenantTransactionManager")
    public int resumeTenantUsersTx(TenantAccount account) {
        String schema = account.getSchemaName();

        if ("public".equals(schema)) return 0;
        if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
        if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

        SchemaContext.bindSchema(schema);
        try {
            List<TenantUser> users = tenantUserRepository.findByAccountIdAndDeletedFalse(account.getId());

            // ✅ conta reativada => remove suspensão por conta de todos
            // quem continua suspenso por admin permanece bloqueado pelo suspendedByAdmin
            users.forEach(u -> u.setSuspendedByAccount(false));

            tenantUserRepository.saveAll(users);
            return users.size();
        } finally {
            SchemaContext.unbindSchema();
        }
    }



    
    
    @Transactional(transactionManager = "tenantTransactionManager")
    protected void softDeleteTenantUsersTx(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = getAccountById(accountId);

        if (!tenantSchemaService.validateTenantSchema(account.getSchemaName())) return;
        if (!tenantSchemaService.tableExists(account.getSchemaName(), "users_tenant")) return;

        SchemaContext.bindSchema(account.getSchemaName());
        try {
            List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
            users.forEach(u -> { if (!u.isDeleted()) u.softDelete(); });
            tenantUserRepository.saveAll(users);
        } finally {
            SchemaContext.unbindSchema();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    protected void restoreTenantUsersTx(Long accountId) {
        SchemaContext.unbindSchema();
        TenantAccount account = getAccountById(accountId);

        if (!tenantSchemaService.validateTenantSchema(account.getSchemaName())) return;
        if (!tenantSchemaService.tableExists(account.getSchemaName(), "users_tenant")) return;

        SchemaContext.bindSchema(account.getSchemaName());
        try {
            List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
            users.forEach(u -> { if (u.isDeleted()) u.restore(); });
            tenantUserRepository.saveAll(users);
        } finally {
            SchemaContext.unbindSchema();
        }
    }

    @Transactional(readOnly = true)
    public TenantAccount getAccountById(Long accountId) {
        SchemaContext.unbindSchema();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    protected void migrateTenant(TenantAccount account) {
        String schemaName = account.getSchemaName();
        SchemaContext.bindSchema(schemaName);
        try {
            tenantSchemaService.ensureSchemaAndMigrate(schemaName);
        } finally {
            SchemaContext.unbindSchema();
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

   @Transactional(transactionManager = "tenantTransactionManager")
   public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
       SchemaContext.unbindSchema();

       // resolve schema no PUBLIC
       TenantAccount account = accountRepository.findByIdAndDeletedFalse(accountId)
               .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

       String schema = account.getSchemaName();
       if (!tenantSchemaService.validateTenantSchema(schema) || !tenantSchemaService.tableExists(schema, "users_tenant")) {
           throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant inválido", 404);
       }

       SchemaContext.bindSchema(schema);
       try {
           int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
           if (updated == 0) {
               throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
           }
       } finally {
           SchemaContext.unbindSchema();
       }
   }

   
}