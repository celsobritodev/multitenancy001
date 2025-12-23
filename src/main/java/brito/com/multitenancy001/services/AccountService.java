package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.entities.account.*;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

	public List<TenantUserResponse> listTenantUsers(Long accountId, boolean onlyActive) {

		// PUBLIC
		TenantContext.unbindTenant();

		Account account = accountRepository.findByIdAndDeletedFalse(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		if (!validateTenantSchema(account.getSchemaName())) {
			throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
		}

		String schema = account.getSchemaName();

		// 🔥 BIND ANTES DA TX
		TenantContext.bindTenant(schema);
		try {

			return listTenantUsersTx(account.getId(), onlyActive);

		} finally {

			TenantContext.unbindTenant();
		}

	}

	@Transactional(readOnly = true)
	protected List<TenantUserResponse> listTenantUsersTx(Long accountId, boolean onlyActive) {

		log.info("🧪 TX START | tenant={}", TenantContext.getCurrentTenant());

		List<UserTenant> users = onlyActive
				? userTenantRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId)
				: userTenantRepository.findByAccountId(accountId);

		return users.stream().map(TenantUserResponse::from).toList();
	}
	
	
	

	public AccountStatusChangeResponse changeAccountStatus(Long accountId, StatusRequest statusReq) {

		TenantContext.unbindTenant();

		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		if (account.isSystemAccount()) {
			log.warn("⛔ Tentativa de alterar conta do sistema | accountId={}", accountId);
			throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", "Contas do sistema não podem ter seu status alterado",
					403);
		}

		AccountStatus accountCurrentStatus = account.getStatus();
		AccountStatus accountNewStatus = statusReq.status();

		if (accountCurrentStatus == accountNewStatus) {
			log.info("↩️ Status já aplicado | accountId={} | status={}", accountId, accountCurrentStatus);
			return buildResponse(account, accountCurrentStatus, false, 0);

		}

		if (accountCurrentStatus == AccountStatus.CANCELLED) {
			throw new ApiException("ACCOUNT_CANCELLED", "Conta cancelada não pode ter status alterado", 409);
		}

		if (accountNewStatus == AccountStatus.FREE_TRIAL && accountCurrentStatus != AccountStatus.FREE_TRIAL) {
			throw new ApiException("INVALID_STATUS_TRANSITION", "Não é permitido voltar para FREE_TRIAL", 409);
		}

		log.info("🔄 Alterando status | {} → {} | motivo={}", accountCurrentStatus, accountNewStatus,
				statusReq.reason());

		account.setStatus(accountNewStatus);

		if (accountNewStatus == AccountStatus.ACTIVE) {
			account.setDeletedAt(null);
		}

		accountRepository.save(account);
		log.info("💾 Conta salva em PUBLIC | accountId={}", accountId);

		boolean tenantUsersSuspended = false;
		int tenantUsersCount = 0;

		if (accountNewStatus == AccountStatus.SUSPENDED) {
			tenantUsersCount = suspendTenantUsersTx(account);
			tenantUsersSuspended = true;
		}

		if (accountNewStatus == AccountStatus.CANCELLED) {
			tenantUsersCount = cancelAccountTx(account);
			tenantUsersSuspended = true;
		}

		log.info("✅ [changeAccountStatus] FINALIZADO | accountId={}", accountId);

		return buildResponse(account, accountCurrentStatus, tenantUsersSuspended, tenantUsersCount);

	}

	private AccountStatusChangeResponse buildResponse(Account account, AccountStatus previousStatus,
			boolean tenantUsersSuspended, int tenantUsersCount) {
		return new AccountStatusChangeResponse(account.getId(), account.getStatus().name(), previousStatus.name(),
				LocalDateTime.now(), account.getSchemaName(),
				new AccountStatusChangeResponse.SideEffects(tenantUsersSuspended, tenantUsersCount));
	}

	public int cancelAccount(Account account) {
		String tenantSchema = account.getSchemaName();

		log.info("🛑 [cancelAccountTx] INÍCIO | accountId={} | schema={}", account.getId(), tenantSchema);
		TenantContext.bindTenant(tenantSchema);
		account.setDeletedAt(LocalDateTime.now());
		accountRepository.save(account);

		if (!validateTenantSchema(tenantSchema) || !tableExistsInTenant(tenantSchema, "users_tenant")) {
			log.warn("⚠️ Cancelamento apenas PUBLIC | schema inválido");
			return 0;
		}
		try {
			return cancelAccountTx(account);
		} finally {
			TenantContext.unbindTenant();
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int cancelAccountTx(Account account) {
		List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
		users.forEach(UserTenant::softDelete);
		userTenantRepository.saveAll(users);
		log.info("✅ Usuários cancelados | {}", users.size());
		return users.size();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int suspendTenantUsersTx(Account account) {

		String tenantSchema = account.getSchemaName();

		log.info("⏸️ [suspendTenantUsersTx] INÍCIO | accountId={} | schema={}", account.getId(), tenantSchema);

		if ("public".equals(tenantSchema)) {
			log.error("❌ ERRO CRÍTICO: schema public");
			return 0;
		}

		if (!validateTenantSchema(tenantSchema)) {
			log.warn("⚠️ Schema inexistente | {}", tenantSchema);
			return 0;
		}

		if (!tableExistsInTenant(tenantSchema, "users_tenant")) {
			log.warn("⚠️ Tabela users_tenant inexistente | {}", tenantSchema);
			return 0;
		}

		TenantContext.bindTenant(tenantSchema);
		log.debug("🔧 TenantContext configurado | {}", tenantSchema);

		try {
			List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());

			log.info("📊 Usuários encontrados: {}", users.size());

			users.forEach(u -> u.setActive(false));
			userTenantRepository.saveAll(users);
			log.info("✅ Usuários suspensos com sucesso | {}", tenantSchema);
			return users.size();

		} catch (Exception e) {

			log.error("💥 ERRO suspendTenantUsersTx", e);
			return 0;
		} finally {
			TenantContext.unbindTenant();
			log.debug("🧹 TenantContext limpo");
		}
	}

	// 🔥 NOVO MÉTODO: Verifica se uma tabela existe no tenant
	private boolean tableExistsInTenant(String schemaName, String tableName) {
		try {
			String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.tables "
					+ "WHERE table_schema = ? AND table_name = ?)";
			Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName);
			return Boolean.TRUE.equals(exists);
		} catch (Exception e) {
			log.error("Erro ao verificar tabela {} no schema {}: {}", tableName, schemaName, e.getMessage());
			return false;
		}
	}

	public boolean validateTenantSchema(String schemaName) {
		if ("public".equals(schemaName)) {
			return false;
		}

		try {
			String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
			Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName);
			return Boolean.TRUE.equals(exists);
		} catch (Exception e) {
			log.error("Erro ao verificar schema: {}", e.getMessage());
			return false;
		}
	}

	@Transactional(readOnly = true)
	public AccountResponse getAccountDetails(Long accountId) {
		TenantContext.unbindTenant();

		Account account = accountRepository.findByIdAndDeletedFalse(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		// Busca admin da conta (platform admin)
		UserAccount admin = userAccountRepository.findFirstByAccountIdAndDeletedFalse(account.getId()).orElse(null);

		return mapToResponse(account, admin);
	}

	private AccountResponse mapToResponse(Account account, UserAccount admin) {
		AdminUserResponse adminResponse = admin != null
				? new AdminUserResponse(admin.getId(), admin.getUsername(), admin.getEmail(), admin.isActive())
				: null;

		return AccountResponse.builder().id(account.getId()).name(account.getName()).schemaName(account.getSchemaName())
				.status(account.getStatus().name()).createdAt(account.getCreatedAt())
				.trialEndDate(account.getTrialEndDate()).admin(adminResponse).systemAccount(account.isSystemAccount())
				.build();
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> listAllAccounts() {

		return accountRepository.findAllByDeletedFalse().stream().map(AccountResponse::fromEntity).toList();
	}

	public AccountResponse createAccount(AccountCreateRequest request) {

		log.info("🚀 Criando conta: {}", request.name());
		TenantContext.unbindTenant(); // 🔥 PUBLIC

		try {

			// 1️⃣ PUBLIC — cria account
			Account account = createAccountTx(request);

			// 2️⃣ TENANT — cria schema + tabelas
			migrateTenant(account);

			// 3️⃣ PUBLIC — cria admin da plataforma
			UserAccount accountAdmin = createAccountAdminTx(request.admin(), account);

			// 4️⃣ TENANT — cria admin do tenant
			tenantSchemaService.createTenantAdmin(account.getId(), account.getSchemaName(), request.admin());

			log.info("✅ Conta criada com sucesso. AccountId={}", account.getId());
			return mapToResponse(account, accountAdmin);

		} catch (Exception e) {
			log.error("❌ Erro ao criar conta", e);
			throw e;
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

		log.debug("🏗️ Criando account no PUBLIC");
		TenantContext.unbindTenant();

		Account account = Account.builder().name(request.name()).schemaName(generateSchemaName(request.name()))
				.slug(generateSlug(request.name())).companyEmail(request.companyEmail())
				.companyDocument(request.companyDocument()).createdAt(LocalDateTime.now())
				.trialEndDate(LocalDateTime.now().plusDays(30)).status(AccountStatus.FREE_TRIAL).systemAccount(false)
				.build();

		return accountRepository.save(account);
	}

	@Transactional
	protected UserAccount createAccountAdminTx(AdminCreateRequest adminReq, Account account) {

		log.debug("👤 Criando admin da conta no PUBLIC");
		TenantContext.unbindTenant();

		UserAccount admin = UserAccount.builder().name("Administrador").username(adminReq.username().toLowerCase())
				.email(adminReq.email()).password(passwordEncoder.encode(adminReq.password()))
				.role(UserAccountRole.PLATFORM_ADMIN).account(account).active(true).build();

		return userAccountRepository.save(admin);
	}

	protected void migrateTenant(Account account) {

		String schemaName = account.getSchemaName();
		log.info("🏗️ Migrando tenant: {}", schemaName);

		TenantContext.bindTenant(schemaName);

		try {
			if (!validateTenantSchema(schemaName)) {
				log.warn("📦 Criando schema {}", schemaName);
				jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
			}

			tenantMigrationService.migrateTenant(schemaName);
			log.info("✅ Tenant migrado com sucesso: {}", schemaName);

		} finally {
			TenantContext.unbindTenant();
		}
	}

	@Transactional
	public void softDeleteAccount(Long accountId) {
		// Valida se não é conta do sistema
		Account account = getAccountById(accountId);
		if (account.isSystemAccount()) {
			throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", "Não é permitido excluir contas do sistema", 403);
		}

		softDeleteAccountTx(accountId); // public
		softDeleteTenantUsersTx(accountId); // tenant
	}

	@Transactional
	protected void softDeleteAccountTx(Long accountId) {
		TenantContext.unbindTenant();
		Account account = getAccountById(accountId);
		account.softDelete();
		accountRepository.save(account);
	}

	@Transactional
	protected void softDeleteTenantUsersTx(Long accountId) {
		Account account = getAccountById(accountId);
		TenantContext.bindTenant(account.getSchemaName());
		try {
			List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
			users.forEach(u -> {
				if (!u.isDeleted())
					u.softDelete();
			});
			userTenantRepository.saveAll(users);
		} finally {
			TenantContext.unbindTenant();
		}
	}

	public void restoreAccount(Long accountId) {
		Account account = getAccountById(accountId);

		// Valida se não é conta do sistema (opcional, mas recomendado)
		if (account.isSystemAccount() && account.isDeleted()) {
			throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
					"Contas do sistema não podem ser restauradas via este endpoint", 403);
		}

		restoreAccountTx(accountId); // public
		restoreTenantUsersTx(accountId); // tenant
	}

	@Transactional
	protected void restoreAccountTx(Long accountId) {
		TenantContext.unbindTenant();
		Account account = getAccountById(accountId);
		account.restore();
		accountRepository.save(account);
	}

	@Transactional
	protected void restoreTenantUsersTx(Long accountId) {
		Account account = getAccountById(accountId);
		TenantContext.bindTenant(account.getSchemaName());
		try {
			List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
			users.forEach(u -> {
				if (u.isDeleted())
					u.restore();
			});
			userTenantRepository.saveAll(users);
		} finally {
			TenantContext.unbindTenant();
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

	@Transactional(readOnly = true)
	public List<AccountResponse> listAllAccountsWithAdmin() {
		TenantContext.unbindTenant();

		return accountRepository.findAllByDeletedFalse().stream().map(account -> {
			UserAccount admin = userAccountRepository.findFirstByAccountIdAndDeletedFalse(account.getId()).orElse(null);
			return mapToResponse(account, admin);
		}).toList();
	}

	@Transactional(readOnly = true)
	public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {

		TenantContext.unbindTenant(); // 🔥 PUBLIC SEMPRE

		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		UserAccount admin = userAccountRepository.findFirstByAccountIdAndDeletedFalse(account.getId()).orElse(null);

		long totalUsers = userAccountRepository.countByAccountIdAndDeletedFalse(account.getId());

		boolean inTrial = account.getStatus() == AccountStatus.FREE_TRIAL;
		boolean trialExpired = inTrial && account.getTrialEndDate().isBefore(LocalDateTime.now());

		long daysRemaining = inTrial
				? Math.max(0, java.time.Duration.between(LocalDateTime.now(), account.getTrialEndDate()).toDays())
				: 0;

		return new AccountAdminDetailsResponse(account.getId(), account.getName(), account.getSlug(),
				account.getSchemaName(), account.getStatus().name(),

				account.getCompanyDocument(), account.getCompanyEmail(),

				account.getCreatedAt(), account.getTrialEndDate(), account.getPaymentDueDate(), account.getDeletedAt(),

				inTrial, trialExpired, daysRemaining,

				admin != null
						? new AdminUserResponse(admin.getId(), admin.getUsername(), admin.getEmail(), admin.isActive())
						: null,

				totalUsers, !account.isDeleted());
	}

	@Transactional(readOnly = true)
	public AccountResponse getAccountByIdWithAdmin(Long accountId) {

		TenantContext.unbindTenant(); // sempre PUBLIC

		Account account = accountRepository.findByIdAndDeletedFalse(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		UserAccount admin = userAccountRepository.findFirstByAccountIdAndDeletedFalse(account.getId()).orElse(null);

		return mapToResponse(account, admin);
	}

}