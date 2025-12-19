package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.entities.account.*;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
	private final TenantSchemaService tenantSchemaService;
	private final JdbcTemplate jdbcTemplate;

	
	
	@Transactional
	public void changeAccountStatus(Long accountId, StatusRequest req) {

	    TenantContext.clear(); // 🔥 GARANTE PUBLIC

	    Account account = accountRepository.findById(accountId)
	        .orElseThrow(() -> new ApiException(
	            "ACCOUNT_NOT_FOUND",
	            "Conta não encontrada",
	            404
	        ));

	    AccountStatus current = account.getStatus();
	    AccountStatus target = req.status();

	    if (current == target) {
	        return; // idempotente
	    }

	    // ❌ regra: conta cancelada não volta
	    if (current == AccountStatus.CANCELLED) {
	        throw new ApiException(
	            "ACCOUNT_CANCELED",
	            "Conta cancelada não pode ter status alterado",
	            409
	        );
	    }

	    // ❌ regra: não pode voltar para FREE_TRIAL
	    if (target == AccountStatus.FREE_TRIAL && current != AccountStatus.FREE_TRIAL) {
	        throw new ApiException(
	            "INVALID_STATUS_TRANSITION",
	            "Não é permitido voltar para FREE_TRIAL",
	            409
	        );
	    }

	    log.info(
	        "🔄 Alterando status da conta {}: {} → {} | motivo={}",
	        account.getId(),
	        current,
	        target,
	        req.reason()
	    );

	    account.setStatus(target);

	    // efeitos colaterais controlados
	    switch (target) {

	        case ACTIVE -> {
	            account.setDeletedAt(null);
	        }

	        case SUSPENDED -> {
	            suspendTenantUsers(account);
	        }

	        case CANCELLED -> {
	            cancelAccount(account);
	        }

	        default -> {}
	    }

	    accountRepository.save(account);
	}
	
	private void suspendTenantUsers(Account account) {

	    TenantContext.setCurrentTenant(account.getSchemaName());

	    try {
	        List<UserTenant> users =
	            userTenantRepository.findByAccountId(account.getId());

	        users.forEach(u -> u.setActive(false));

	        userTenantRepository.saveAll(users);

	    } finally {
	        TenantContext.clear();
	    }
	}
	
	private void cancelAccount(Account account) {

	    // PUBLIC
	    account.setDeletedAt(LocalDateTime.now());

	    // TENANT
	    TenantContext.setCurrentTenant(account.getSchemaName());
	    try {
	        List<UserTenant> users =
	            userTenantRepository.findByAccountId(account.getId());

	        users.forEach(UserTenant::softDelete);
	        userTenantRepository.saveAll(users);
	    } finally {
	        TenantContext.clear();
	    }
	}



	
	

	@Transactional(readOnly = true)
	public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {

	    TenantContext.clear(); // 🔥 PUBLIC SEMPRE

	    Account account = accountRepository.findById(accountId)
	        .orElseThrow(() -> new ApiException(
	            "ACCOUNT_NOT_FOUND",
	            "Conta não encontrada",
	            404
	        ));

	    UserAccount admin = userAccountRepository
	        .findFirstByAccountIdAndDeletedFalse(account.getId())
	        .orElse(null);

	    long totalUsers = userAccountRepository.countByAccountIdAndDeletedFalse(account.getId());

	    boolean inTrial = account.getStatus() == AccountStatus.FREE_TRIAL;
	    boolean trialExpired = inTrial && account.getTrialEndDate().isBefore(LocalDateTime.now());

	    long daysRemaining = inTrial
	        ? Math.max(0,
	            java.time.Duration.between(
	                LocalDateTime.now(),
	                account.getTrialEndDate()
	            ).toDays()
	          )
	        : 0;

	    return new AccountAdminDetailsResponse(
	        account.getId(),
	        account.getName(),
	        account.getSlug(),
	        account.getSchemaName(),
	        account.getStatus().name(),

	        account.getCompanyDocument(),
	        account.getCompanyEmail(),

	        account.getCreatedAt(),
	        account.getTrialEndDate(),
	        account.getPaymentDueDate(),
	        account.getDeletedAt(),

	        inTrial,
	        trialExpired,
	        daysRemaining,

	        admin != null
	            ? new AdminUserResponse(
	                admin.getId(),
	                admin.getUsername(),
	                admin.getEmail(),
	                admin.isActive()
	              )
	            : null,

	        totalUsers,
	        !account.isDeleted()
	    );
	}

	
	
	@Transactional(readOnly = true)
	public AccountResponse getAccountDetails(Long accountId) {
	    TenantContext.clear(); // 🔥 PUBLIC SEMPRE

	    Account account = accountRepository.findByIdAndDeletedFalse(accountId)
	        .orElseThrow(() -> new ApiException(
	            "ACCOUNT_NOT_FOUND",
	            "Conta não encontrada",
	            404
	        ));

	    // Busca admin da conta (platform admin)
	    UserAccount admin = userAccountRepository
	        .findFirstByAccountIdAndDeletedFalse(account.getId())
	        .orElse(null);

	    return mapToResponse(account, admin);
	}

	
	
	
	
	@Transactional(readOnly = true)
	public List<AccountResponse> listAllAccounts() {

		return accountRepository.findAllByDeletedFalse().stream().map(AccountResponse::fromEntity).toList();
	}

	

	@Transactional
	public AccountResponse createAccount(AccountCreateRequest request) {
		log.info("🚀 Criando conta: {}", request.name());
		TenantContext.clear();

		try {
			// 1. Criar account (PUBLIC)
			Account account = createAccountTx(request);

			// 2. Criar schema + tabelas
			migrateTenant(account);

			// 3. Criar admin da plataforma
			UserAccount accountAdmin = createAccountAdminTx(request.admin(), account);

			// 4. Criar admin do tenant
			tenantSchemaService.createTenantAdmin(account.getId(), account.getSchemaName(), request.admin());

			log.info("✅ Conta criada com sucesso. AccountId={}", account.getId());
			return mapToResponse(account, accountAdmin);

		} catch (DataIntegrityViolationException e) {

			log.warn("❌ Tentativa de criar conta duplicada. Documento ou Email já existem.");

			throw new ApiException("ACCOUNT_ALREADY_EXISTS",
					"Já existe uma conta cadastrada com este documento ou email", 409);

		} catch (ApiException e) {
			// Repassa erros de negócio já tratados
			throw e;

		} catch (Exception e) {

			log.error("❌ Erro inesperado ao criar conta", e);

			throw new ApiException("ACCOUNT_CREATION_FAILED", "Erro inesperado ao criar a conta", 500);
		}
	}

	/**
	 * Verifica se o fluxo de criação está funcionando
	 */
	public boolean testTenantCreation(String schemaName) {
		try {
			// Verifica apenas se o schema existe, sem tentar acessar tabelas
			String sql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
			List<String> schemas = jdbcTemplate.queryForList(sql, String.class, schemaName);
			return !schemas.isEmpty();
		} catch (Exception e) {
			log.error("Teste de tenant falhou: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Método de recuperação: cria admin se o schema existir mas não tiver admin
	 */
	public UserTenant recoverTenantAdmin(Long accountId, String schemaName, AdminCreateRequest adminReq) {
		log.warn("⚠️ Executando recuperação de admin para tenant: {}", schemaName);

		// Primeiro verifica se o schema está pronto
		if (!tenantSchemaService.isSchemaReady(schemaName)) {
			throw new ApiException("SCHEMA_NOT_READY", "Schema do tenant não está pronto para uso", 500);
		}

		// Tenta criar o admin
		return tenantSchemaService.createTenantAdmin(accountId, schemaName, adminReq);
	}

	/*
	 * ========================= ACCOUNT (PUBLIC) =========================
	 */

	@Transactional
	protected Account createAccountTx(AccountCreateRequest request) {
		TenantContext.clear();

		Account account = Account.builder().name(request.name()).schemaName(generateSchemaName(request.name()))
				.slug(generateSlug(request.name())).companyEmail(request.companyEmail())
				.companyDocument(request.companyDocument()).createdAt(LocalDateTime.now())
				.trialEndDate(LocalDateTime.now().plusDays(30)).status(AccountStatus.FREE_TRIAL).build();

		return accountRepository.save(account);
	}

	@Transactional
	protected UserAccount createAccountAdminTx(AdminCreateRequest adminReq, Account account) {
		TenantContext.clear();

		String username = adminReq.username().toLowerCase().trim();
		if (userAccountRepository.existsByUsernameAndAccountId(username, account.getId())) {
			throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe para esta conta", 409);
		}

		UserAccount admin = UserAccount.builder().name("Administrador").username(username).email(adminReq.email())
				.password(passwordEncoder.encode(adminReq.password())).role(UserAccountRole.PLATFORM_ADMIN).account(account)
				.active(true).build();

		return userAccountRepository.save(admin);
	}

	protected void migrateTenant(Account account) {
		TenantContext.setCurrentTenant(account.getSchemaName());
		try {
			tenantMigrationService.migrateTenant(account.getSchemaName());
		} finally {
			TenantContext.clear();
		}
	}

	/*
	 * ========================= SOFT DELETE / RESTORE =========================
	 */

	public void softDeleteAccount(Long accountId) {
		softDeleteAccountTx(accountId); // public
		softDeleteTenantUsersTx(accountId); // tenant
	}

	@Transactional
	protected void softDeleteAccountTx(Long accountId) {
		TenantContext.clear();
		Account account = getAccountById(accountId);
		account.softDelete();
		accountRepository.save(account);
	}

	@Transactional
	protected void softDeleteTenantUsersTx(Long accountId) {
		Account account = getAccountById(accountId);
		TenantContext.setCurrentTenant(account.getSchemaName());
		try {
			List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
			users.forEach(u -> {
				if (!u.isDeleted())
					u.softDelete();
			});
			userTenantRepository.saveAll(users);
		} finally {
			TenantContext.clear();
		}
	}

	public void restoreAccount(Long accountId) {
		restoreAccountTx(accountId); // public
		restoreTenantUsersTx(accountId); // tenant
	}

	@Transactional
	protected void restoreAccountTx(Long accountId) {
		TenantContext.clear();
		Account account = getAccountById(accountId);
		account.restore();
		accountRepository.save(account);
	}

	@Transactional
	protected void restoreTenantUsersTx(Long accountId) {
		Account account = getAccountById(accountId);
		TenantContext.setCurrentTenant(account.getSchemaName());
		try {
			List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
			users.forEach(u -> {
				if (u.isDeleted())
					u.restore();
			});
			userTenantRepository.saveAll(users);
		} finally {
			TenantContext.clear();
		}
	}

	/*
	 * ========================= AUXILIARES =========================
	 */

	@Transactional(readOnly = true)
	public Account getAccountById(Long accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
	}

	private String generateSlug(String name) {
		String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		String slug = base;
		int i = 1;
		while (accountRepository.findBySlugAndDeletedFalse(slug).isPresent()) {
			slug = base + "-" + i++;
		}
		return slug;
	}

	private String generateSchemaName(String name) {
		return "tenant_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_") + "_"
				+ UUID.randomUUID().toString().substring(0, 8);
	}

	private AccountResponse mapToResponse(Account account, UserAccount admin) {
		return new AccountResponse(account.getId(), account.getName(), account.getSchemaName(),
				account.getStatus().name(), account.getCreatedAt(), account.getTrialEndDate(),
				new AdminUserResponse(admin.getId(), admin.getUsername(), admin.getEmail(), admin.isActive()));
	}
}
