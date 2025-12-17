package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.AccountCreateRequest;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.dtos.AdminCreateRequest;
import brito.com.multitenancy001.dtos.AdminUserResponse;
import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.account.AccountStatus;
import brito.com.multitenancy001.entities.account.UserAccount;
import brito.com.multitenancy001.entities.account.UserRole;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserAccountRepository;
import brito.com.multitenancy001.repositories.UserTenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserTenantRepository userTenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantMigrationService tenantMigrationService;
    public List<AccountResponse> listAllAccounts() {
        TenantContext.clear();
        return accountRepository
                .findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getSchemaName(),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getTrialEndDate(),
                null
        );
    }

 
   
        public AccountResponse createAccount(AccountCreateRequest request) {
            log.info("🚀 Iniciando criação de conta: {}", request.name());
            
            try {
            	
            	TenantContext.clear();
            	
                // 1. Cria conta no ACCOUNT
                Account account = saveAccountAccount(request);
                log.info("✅ Conta criada no account. ID: {}, Schema: {}", 
                        account.getId(), account.getSchemaName());
                
                //✔ Flyway já cria schema automaticamente
                // 2. Cria schema e migra tenant
                //log.info("🔄 Criando schema: {}", account.getSchemaName());
                //tenantService.createSchema(account.getSchemaName());
                //log.info("✅ Schema criado: {}", account.getSchemaName());
                
                log.info("🔄 Iniciando migração Flyway...");
                TenantContext.setCurrentTenant(account.getSchemaName());
                tenantMigrationService.migrateTenant(account.getSchemaName());
                log.info("✅ Migração Flyway concluída");
                
                // 3. Cria admin no ACCOUNT (users_account)
                log.info("🔄 Criando admin account...");
                TenantContext.clear();
                UserAccount accountAdmin = createAccountAdmin(request.admin(), account);
                log.info("✅ Admin account criado. ID: {}, Username: {}", 
                        accountAdmin.getId(), accountAdmin.getUsername());
     
                
                
                // 4. Cria admin no tenant
                log.info("🔄 Criando admin tenant...");
                TenantContext.setCurrentTenant(account.getSchemaName());
                UserTenant tenantAdmin = createTenantAdmin(account, request.admin());
                log.info("✅ Admin tenant criado. ID: {}, Username: {}", 
                        tenantAdmin.getId(), tenantAdmin.getUsername());
                
                
                
                
                
                
                
                
                
                return mapToResponse(account, accountAdmin);
                
                
                
                
                
                
                
                
            } catch (Exception e) {
                log.error("❌ Erro ao criar conta: {}", e.getMessage(), e);
                throw e;
            }
        }
        
        private UserTenant createTenantAdmin(Account account, AdminCreateRequest adminReq) {
            log.info("🔧 Criando admin tenant no schema: {}", account.getSchemaName());
            
            // Definir schema do tenant
            TenantContext.setCurrentTenant(account.getSchemaName());
            
            try {
                // Normalizar username
                String username = adminReq.username().toLowerCase().trim();
                log.info("🔧 Username normalizado: {}", username);
                
                // Verificar unicidade no tenant
                boolean exists = userTenantRepository.existsByUsernameAndAccountId(username, account.getId());
                log.info("🔧 Verificando unicidade: {}", exists);
                
                if (exists) {
                    throw new ApiException(
                        "USERNAME_ALREADY_EXISTS",
                        "Username já existe para esta conta no tenant",
                        409
                    );
                }
                
                // Criar usuário no tenant
                log.info("🔧 Construindo UserTenant...");
                UserTenant tenantAdmin = UserTenant.builder()
                    .name("Administrador")
                    .username(username)
                    .email(adminReq.email())
                    .password(passwordEncoder.encode(adminReq.password()))
                    .role(UserRole.ADMIN)
                    .accountId(account.getId())
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
                    
                log.info("🔧 Salvando UserTenant...");
                UserTenant saved = userTenantRepository.save(tenantAdmin);
                log.info("✅ UserTenant salvo. ID: {}", saved.getId());
                
                return saved;
            } finally {
                TenantContext.clear();
                log.info("🔧 TenantContext limpo");
            }
        }
   
    
    
    
    
    @Transactional
    protected Account saveAccountAccount(AccountCreateRequest request) {
        String schemaName = generateSchemaName(request.name());
        LocalDateTime now = LocalDateTime.now();

        Account account = Account.builder()
                .name(request.name())
                .schemaName(schemaName)
                .slug(generateSlug(request.name()))
                .companyEmail(request.companyEmail())
                .companyDocument(request.companyDocument())
                .createdAt(now)
                .trialEndDate(now.plusDays(30))
                .status(AccountStatus.FREE_TRIAL)
                .build();

        return accountRepository.save(account);
    }
    
    private UserAccount createAccountAdmin(AdminCreateRequest adminReq, Account account) {
        // Normalizar username
        String username = adminReq.username().toLowerCase().trim();
        
        // Verificar unicidade no account
        if (userAccountRepository.existsByUsernameAndAccountId(username, account.getId())) {
            throw new ApiException(
                "USERNAME_ALREADY_EXISTS",
                "Username já existe para esta conta no sistema account",
                409
            );
        }
        
        UserAccount admin = UserAccount.builder()
            .name("Administrador")
            .username(username)
            .email(adminReq.email())
            .password(passwordEncoder.encode(adminReq.password()))
            .role(UserRole.ADMIN)
            .account(account)
            .active(true)
            .build();
            
        return userAccountRepository.save(admin);
    }
    

    
 // No método generateSlug, ajustar a verificação:
    private String generateSlug(String accountName) {
        String slug = accountName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        
        // Garantir unicidade do slug
        String baseSlug = slug;
        int counter = 1;
        
        // Verificar se já existe um slug ativo (não deletado)
        while (accountRepository.findBySlugAndDeletedFalse(slug).isPresent()) {
            slug = baseSlug + "-" + counter;
            counter++;
        }
        
        return slug;
    }
    
    
    
    
    
    
    
    
    private String generateSchemaName(String accountName) {
        String baseName = accountName.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (baseName.length() > 50) {
            baseName = baseName.substring(0, 50);
        }

        return "tenant_" + baseName + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private AccountResponse mapToResponse(Account account, UserAccount adminUser) {
        AdminUserResponse adminDto = new AdminUserResponse(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getEmail(),
                adminUser.isActive()
        );

        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getSchemaName(),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getTrialEndDate(),
                adminDto
        );
    }

    @Transactional(readOnly = true)
    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada.",
                        404
                ));
    }

    @Transactional(readOnly = true)
    public Account getAccountBySchemaName(String schemaName) {
        return accountRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada.",
                        404
                ));
    }

    @Transactional
    public void updateAccountStatus(Long accountId, AccountStatus newStatus) {
        Account account = getAccountById(accountId);
        account.setStatus(newStatus);
        accountRepository.save(account);
    }

    @Transactional
    public void extendTrial(Long accountId, int days) {
        Account account = getAccountById(accountId);
        account.setTrialEndDate(
                account.getTrialEndDate() == null
                        ? LocalDateTime.now().plusDays(days)
                        : account.getTrialEndDate().plusDays(days)
        );
        accountRepository.save(account);
    }

    @Transactional
    public void updatePaymentDueDate(Long accountId, LocalDateTime newDueDate) {
        Account account = getAccountById(accountId);
        account.setPaymentDueDate(newDueDate);
        accountRepository.save(account);
    }

    @Transactional
    public void softDeleteAccount(Long accountId) {
        Account account = getAccountById(accountId);
        
        // Soft delete da conta no account
        account.softDelete();
        accountRepository.save(account);
        
        // 🔹 IMPORTANTE: Também precisamos soft delete dos UserTenant
        // Isso requer acessar o schema do tenant
        softDeleteTenantUsers(account);
    }
    
    private void softDeleteTenantUsers(Account account) {
        // Acessar o schema do tenant para soft delete dos UserTenant
        TenantContext.setCurrentTenant(account.getSchemaName());
        
        try {
            // Buscar todos os UserTenant da conta
            List<UserTenant> tenantUsers = userTenantRepository.findByAccountId(account.getId());
            
            // Fazer soft delete de cada um
            for (UserTenant user : tenantUsers) {
                if (!user.isDeleted()) {
                    user.softDelete();
                }
            }
            
            // Salvar as alterações
            userTenantRepository.saveAll(tenantUsers);
            
            log.info("Soft delete realizado para {} usuários tenant da conta {}", 
                    tenantUsers.size(), account.getId());
                    
        } catch (Exception e) {
            log.error("Erro ao realizar soft delete de usuários tenant da conta {}: {}", 
                    account.getId(), e.getMessage());
            // Não lançar exceção aqui para não reverter o soft delete da conta
        } finally {
            TenantContext.clear();
        }
    }
    
    
    @Transactional
    public void restoreAccount(Long accountId) {
        Account account = getAccountById(accountId);
        
        if (!account.isDeleted()) {
            throw new ApiException(
                    "ACCOUNT_NOT_DELETED",
                    "A conta não está deletada e não pode ser restaurada",
                    409
            );
        }
        
        // Restaurar conta no account
        account.restore();
        accountRepository.save(account);
        
        // 🔹 Restaurar UserTenant
        restoreTenantUsers(account);
    } 
    
    
    private void restoreTenantUsers(Account account) {
        TenantContext.setCurrentTenant(account.getSchemaName());
        
        try {
            List<UserTenant> tenantUsers = userTenantRepository.findByAccountId(account.getId());
            
            for (UserTenant user : tenantUsers) {
                if (user.isDeleted()) {
                    user.restore();
                }
            }
            
            userTenantRepository.saveAll(tenantUsers);
            
            log.info("Restauração realizada para {} usuários tenant da conta {}", 
                    tenantUsers.size(), account.getId());
                    
        } catch (Exception e) {
            log.error("Erro ao restaurar usuários tenant da conta {}: {}", 
                    account.getId(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
   
    
    

    @Transactional(readOnly = true)
    public boolean isAccountActive(Long accountId) {
        return getAccountById(accountId).isActive();
    }

    @Transactional(readOnly = true)
    public long getDaysRemainingInTrial(Long accountId) {
        return getAccountById(accountId).getDaysRemainingInTrial();
    }
}