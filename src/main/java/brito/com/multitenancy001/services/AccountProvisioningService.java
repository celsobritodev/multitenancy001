package brito.com.multitenancy001.services;

import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;
import brito.com.multitenancy001.repositories.publicdb.AccountRepository;
import brito.com.multitenancy001.repositories.tenant.TenantUserRepository;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
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

public class AccountProvisioningService {

    private final AccountRepository accountRepository;
    private final TenantUserRepository userTenantRepository;
    private final TenantSchemaProvisioningService tenantSchemaService;
    private final PasswordEncoder passwordEncoder;
    private final PublicAccountService publicAccountService;


    /* =========================================================
       1. CRIAÇÃO DE CONTA (SIGNUP SIMPLIFICADO)
       ========================================================= */

    public AccountResponse createAccount(SignupRequest request) {
        validateSignupRequest(request);

        TenantContext.unbindTenant(); // garante PUBLIC

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
        TenantContext.bindTenant(account.getSchemaName());
        try {
            String username = generateUsernameFromEmail(request.companyEmail());

            boolean usernameExists = userTenantRepository.existsByUsernameAndAccountId(username, account.getId());
            boolean emailExists = userTenantRepository.existsByEmailAndAccountId(request.companyEmail(), account.getId());

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
            u.setActive(true);
            u.setCreatedAt(LocalDateTime.now());
            u.setTimezone("America/Sao_Paulo");
            u.setLocale("pt_BR");

            return userTenantRepository.save(u);

        } finally {
            TenantContext.unbindTenant();
        }
    }

    

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccounts() {
        TenantContext.unbindTenant();
        return accountRepository.findAllByDeletedFalse()
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByIdWithAdmin(Long accountId) {
        TenantContext.unbindTenant();
        TenantAccount account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
        return AccountResponse.fromEntity(account);
    }

    @Transactional(readOnly = true)
    public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {
        TenantContext.unbindTenant();
        TenantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

       

        return AccountAdminDetailsResponse.from(account, null);


    }

    /* =========================================================
       3. GERENCIAMENTO DE STATUS DE CONTA
       ========================================================= */

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, StatusRequest req) {
        TenantContext.unbindTenant();

        TenantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        if (account.isSystemAccount()) {
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", 
                    "Contas do sistema não podem ter seu status alterado", 403);
        }

        TenantAccountStatus previous = account.getStatus();
        TenantAccountStatus next = req.status();

        if (previous == next) {
            return buildStatusChangeResponse(account, previous, false, 0);
        }

        if (previous == TenantAccountStatus.CANCELLED) {
            throw new ApiException("ACCOUNT_CANCELLED", 
                    "Conta cancelada não pode ter status alterado", 409);
        }

        // Regra: não volta para trial
        if (next == TenantAccountStatus.FREE_TRIAL && previous != TenantAccountStatus.FREE_TRIAL) {
            throw new ApiException("INVALID_STATUS_TRANSITION", 
                    "Não é permitido voltar para FREE_TRIAL", 409);
        }

        // grava em PUBLIC
        account.setStatus(next);
        if (next == TenantAccountStatus.ACTIVE) account.setDeletedAt(null);
        accountRepository.save(account);

        // side-effects no tenant
        int affected = 0;
        boolean applied = false;

        if (next == TenantAccountStatus.SUSPENDED) {
            affected = suspendTenantUsersTx(account);
            applied = true;
        } else if (next == TenantAccountStatus.CANCELLED) {
            affected = cancelAccount(account);
            applied = true;
        }

        return buildStatusChangeResponse(account, previous, applied, affected);
    }

    /* =========================================================
       4. SOFT DELETE / RESTORE
       ========================================================= */

    @Transactional
    public void softDeleteAccount(Long accountId) {
        TenantContext.unbindTenant();
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
        TenantContext.unbindTenant();
        TenantAccount account = getAccountById(accountId);
        account.softDelete();
        accountRepository.save(account);
    }

    public void restoreAccount(Long accountId) {
        TenantContext.unbindTenant();
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
        TenantContext.unbindTenant();
        TenantAccount account = getAccountById(accountId);
        account.restore();
        accountRepository.save(account);
    }

    /* =========================================================
       5. LISTAGEM DE USUÁRIOS DO TENANT (ADMIN)
       ========================================================= */

    public List<TenantUserResponse> listTenantUsers(Long accountId, boolean onlyActive) {
        // resolve conta em PUBLIC
        TenantContext.unbindTenant();
        TenantAccount account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        String schema = account.getSchemaName();

        if (!tenantSchemaService.validateTenantSchema(schema)) {
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
        }
        if (!tenantSchemaService.tableExists(schema, "users_tenant")) {
            throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela users_tenant não existe no tenant", 404);
        }

        TenantContext.bindTenant(schema);
        try {
            return listTenantUsersTx(account.getId(), onlyActive);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    protected List<TenantUserResponse> listTenantUsersTx(Long accountId, boolean onlyActive) {
        List<TenantUser> users = onlyActive
                ? userTenantRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId)
                : userTenantRepository.findByAccountId(accountId);

        return users.stream().map(TenantUserResponse::from).toList();
    }

    /* =========================================================
       MÉTODOS AUXILIARES (PRIVADOS)
       ========================================================= */

    
    
    
    
    
   @Transactional(transactionManager = "tenantTransactionManager")
    protected TenantUser createTenantAdminFromSignup(Long accountId, SignupRequest request) {
        String username = generateUsernameFromEmail(request.companyEmail());
        
        boolean usernameExists = userTenantRepository.existsByUsernameAndAccountId(username, accountId);
        boolean emailExists = userTenantRepository.existsByEmailAndAccountId(request.companyEmail(), accountId);

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
        u.setActive(true);
        u.setCreatedAt(LocalDateTime.now());
        u.setTimezone("America/Sao_Paulo");
        u.setLocale("pt_BR");

        return userTenantRepository.save(u);
    }

    private AccountStatusChangeResponse buildStatusChangeResponse(
            TenantAccount account,
            TenantAccountStatus previous,
            boolean tenantUsersAffected,
            int count
    ) {
        return new AccountStatusChangeResponse(
                account.getId(),
                account.getStatus().name(),
                previous.name(),
                LocalDateTime.now(),
                account.getSchemaName(),
                new AccountStatusChangeResponse.SideEffects(tenantUsersAffected, count)
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

        TenantContext.bindTenant(schema);
        try {
            return cancelAccountTenantTx(account);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(transactionManager = "publicTransactionManager", propagation = Propagation.REQUIRES_NEW)
    protected void cancelAccountPublicTx(TenantAccount account) {
        TenantContext.unbindTenant();
        account.setDeletedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    protected int cancelAccountTenantTx(TenantAccount account) {
        List<TenantUser> users = userTenantRepository.findByAccountId(account.getId());
        users.forEach(TenantUser::softDelete);
        userTenantRepository.saveAll(users);
        return users.size();
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelAccountTx(TenantAccount account) {
        List<TenantUser> users = userTenantRepository.findByAccountId(account.getId());
        users.forEach(TenantUser::softDelete);
        userTenantRepository.saveAll(users);
        return users.size();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public int suspendTenantUsersTx(TenantAccount account) {
        String schema = account.getSchemaName();

        if ("public".equals(schema)) return 0;
        if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
        if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

        TenantContext.bindTenant(schema);
        try {
            List<TenantUser> users = userTenantRepository.findByAccountId(account.getId());
            users.forEach(u -> u.setActive(false));
            userTenantRepository.saveAll(users);
            return users.size();
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    protected void softDeleteTenantUsersTx(Long accountId) {
        TenantContext.unbindTenant();
        TenantAccount account = getAccountById(accountId);

        if (!tenantSchemaService.validateTenantSchema(account.getSchemaName())) return;
        if (!tenantSchemaService.tableExists(account.getSchemaName(), "users_tenant")) return;

        TenantContext.bindTenant(account.getSchemaName());
        try {
            List<TenantUser> users = userTenantRepository.findByAccountId(account.getId());
            users.forEach(u -> { if (!u.isDeleted()) u.softDelete(); });
            userTenantRepository.saveAll(users);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    protected void restoreTenantUsersTx(Long accountId) {
        TenantContext.unbindTenant();
        TenantAccount account = getAccountById(accountId);

        if (!tenantSchemaService.validateTenantSchema(account.getSchemaName())) return;
        if (!tenantSchemaService.tableExists(account.getSchemaName(), "users_tenant")) return;

        TenantContext.bindTenant(account.getSchemaName());
        try {
            List<TenantUser> users = userTenantRepository.findByAccountId(account.getId());
            users.forEach(u -> { if (u.isDeleted()) u.restore(); });
            userTenantRepository.saveAll(users);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(readOnly = true)
    public TenantAccount getAccountById(Long accountId) {
        TenantContext.unbindTenant();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    protected void migrateTenant(TenantAccount account) {
        String schemaName = account.getSchemaName();
        TenantContext.bindTenant(schemaName);
        try {
            tenantSchemaService.ensureSchemaAndMigrate(schemaName);
        } finally {
            TenantContext.unbindTenant();
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
        
        while (userTenantRepository.existsByUsernameAndAccountId(username, accountId)) {
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


   
}