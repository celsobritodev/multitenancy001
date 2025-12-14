package brito.com.multitenancy001.services;


import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.AccountCreateRequest;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.dtos.AdminCreateRequest;
import brito.com.multitenancy001.dtos.AdminUserResponse;
import brito.com.multitenancy001.entities.master.Account;
import brito.com.multitenancy001.entities.master.AccountStatus;
import brito.com.multitenancy001.entities.master.User;
import brito.com.multitenancy001.entities.master.UserRole;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGeneratorService usernameGenerator; // ✅ Injetado
    private final JdbcTemplate jdbcTemplate;
    
    private final UsernameUniquenessService usernameUniquenessService;
    

   
    
    
    public AccountResponse createAccount(AccountCreateRequest request) {

        log.info("Criando nova conta: {}", request.name());

        if (accountRepository.findByName(request.name()).isPresent()) {
            throw new RuntimeException("Nome da conta já existe");
        }

        String schemaName = generateSchemaName(request.name());

        LocalDateTime now = LocalDateTime.now();

        Account account = Account.builder()
                .name(request.name())
                .schemaName(schemaName)
                .companyEmail(request.companyEmail())
                .companyDocument(request.companyDocument())
                .createdAt(now)
                .trialEndDate(now.plusDays(30))
                .status(AccountStatus.FREE_TRIAL)
                .build();

        account = accountRepository.save(account);
        
        createTenantSchema(account.getSchemaName());
        createTenantTables(account.getSchemaName());
        
        
     // ⭐ MUITO IMPORTANTE — AVISA O HIBERNATE QUAL TENANT USAR
        TenantContext.setCurrentTenant(account.getSchemaName());
        
    
        // Criar usuario admin
        AdminCreateRequest   adminReq = request.admin();

        if (!adminReq.password().equals(adminReq.confirmPassword())) {
            throw new RuntimeException("As senhas não coincidem.");
        }

        User adminUser = createAdminUser(
                account,
                adminReq.username(),
                adminReq.email(),
                adminReq.password()
        );

        return mapToResponse(account, adminUser);
    }

    
    
    
    
    private String generateSchemaName(String accountName) {
        String baseName = accountName.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        
        if (baseName.length() > 50) {
            baseName = baseName.substring(0, 50);
        }
        
        return "tenant_" + baseName + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
    
    private User createAdminUser(Account account, String username, String email, String password) {

        // NORMALIZAR username
        username = usernameGenerator.normalize(username);

        // Garantir unicidade dentro da conta
        username = usernameUniquenessService.ensureUniqueUsername(username, account.getId());

        User admin = User.builder()
                .name("Administrador")
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .account(account)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(admin);
    }

    
    
    
    
    
    
    
    
    private AccountResponse mapToResponse(Account account, User adminUser) {

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
                .orElseThrow(() -> new RuntimeException("Conta não encontrada com ID: " + accountId));
    }
    
    @Transactional(readOnly = true)
    public Account getAccountBySchemaName(String schemaName) {
        return accountRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada com schema: " + schemaName));
    }
    
    @Transactional
    public void updateAccountStatus(Long accountId, AccountStatus newStatus) {
        Account account = getAccountById(accountId);
        account.setStatus(newStatus);
        accountRepository.save(account);
        log.info("Status da conta {} atualizado para: {}", accountId, newStatus);
    }
    
    @Transactional
    public void extendTrial(Long accountId, int days) {
        Account account = getAccountById(accountId);
        
        if (account.getTrialEndDate() == null) {
            account.setTrialEndDate(LocalDateTime.now().plusDays(days));  // ✅ LocalDate
        } else {
            account.setTrialEndDate(account.getTrialEndDate().plusDays(days));  // ✅ LocalDate
        }
        
        accountRepository.save(account);
        log.info("Trial da conta {} extendido por {} dias", accountId, days);
    }
    
    @Transactional
    public void updatePaymentDueDate(Long accountId, LocalDateTime newDueDate) {  // ✅ LocalDate
        Account account = getAccountById(accountId);
        account.setPaymentDueDate(newDueDate);  // ✅ LocalDate
        accountRepository.save(account);
        log.info("Data de vencimento da conta {} atualizada para: {}", accountId, newDueDate);
    }
    
    @Transactional
    public void softDeleteAccount(Long accountId) {
        Account account = getAccountById(accountId);
        account.softDelete();
        accountRepository.save(account);
        log.info("Conta {} deletada (soft delete)", accountId);
    }
    
    @Transactional(readOnly = true)
    public boolean isAccountActive(Long accountId) {
        try {
            Account account = getAccountById(accountId);
            return account.isActive();
        } catch (Exception e) {
            log.error("Erro ao verificar status da conta {}: {}", accountId, e.getMessage());
            return false;
        }
    }
    
    @Transactional(readOnly = true)
    public long getDaysRemainingInTrial(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getDaysRemainingInTrial();
    }
    
    private void createTenantSchema(String schema) {
    	jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
    	}


    	private void createTenantTables(String schema) {
    	// users
    	jdbcTemplate.execute("""
    	CREATE TABLE IF NOT EXISTS %s.users (
    	id BIGSERIAL PRIMARY KEY,
    	username VARCHAR(100) NOT NULL,
    	name VARCHAR(100),
    	email VARCHAR(150) NOT NULL,
    	password VARCHAR(255) NOT NULL,
    	role VARCHAR(50),
    	active BOOLEAN DEFAULT TRUE,
    	created_at TIMESTAMP DEFAULT NOW()
    	)
    	""".formatted(schema));


    	// suppliers
    	jdbcTemplate.execute("""
    	CREATE TABLE IF NOT EXISTS %s.suppliers (
    	id UUID PRIMARY KEY,
    	name VARCHAR(200) NOT NULL,
    	document VARCHAR(20) UNIQUE,
    	email VARCHAR(150),
    	phone VARCHAR(20),
    	created_at TIMESTAMP DEFAULT NOW()
    	)
    	""".formatted(schema));


    	// products
    	jdbcTemplate.execute("""
    	CREATE TABLE IF NOT EXISTS %s.products (
    	id UUID PRIMARY KEY,
    	name VARCHAR(200) NOT NULL,
    	sku VARCHAR(100) UNIQUE,
    	price NUMERIC(10,2) NOT NULL,
    	stock_quantity INT DEFAULT 0,
    	supplier_id UUID NULL,
    	created_at TIMESTAMP DEFAULT NOW()
    	)
    	""".formatted(schema));


    	// sales
    	jdbcTemplate.execute("""
    	CREATE TABLE IF NOT EXISTS %s.sales (
    	id UUID PRIMARY KEY,
    	sale_date TIMESTAMP NOT NULL,
    	total_amount NUMERIC(10,2),
    	customer_name VARCHAR(200),
    	customer_email VARCHAR(150),
    	status VARCHAR(20)
    	)
    	""".formatted(schema));


    	// sale_items
    	jdbcTemplate.execute("""
    	CREATE TABLE IF NOT EXISTS %s.sale_items (
    	id BIGSERIAL PRIMARY KEY,
    	sale_id UUID NOT NULL,
    	product_id UUID,
    	quantity INT,
    	unit_price NUMERIC(10,2),
    	FOREIGN KEY (sale_id) REFERENCES %s.sales(id)
    	)
    	""".formatted(schema, schema));
    	}
    	
}