package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.entities.account.*;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.entities.tenant.UserTenantRole;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
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
    private final UserTenantRepository userTenantRepository;
    private final TenantMigrationService tenantMigrationService;
    private final TenantSchemaService tenantSchemaService;
    private final JdbcTemplate jdbcTemplate;

    public List<TenantUserResponse> listTenantUsers(Long accountId, boolean onlyActive) {

        log.info("👥 [listTenantUsers] INÍCIO | accountId={} | onlyActive={} | tenantBefore={}",
                accountId, onlyActive, TenantContext.getCurrentTenant());

        // PUBLIC
        TenantContext.unbindTenant();
        log.debug("🧹 [listTenantUsers] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> {
                    log.warn("❌ [listTenantUsers] ACCOUNT_NOT_FOUND | accountId={}", accountId);
                    return new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
                });

        log.debug("📦 [listTenantUsers] Account carregada | accountId={} | schema={}",
                account.getId(), account.getSchemaName());

        if (!validateTenantSchema(account.getSchemaName())) {
            log.warn("❌ [listTenantUsers] TENANT_SCHEMA_NOT_FOUND | schema={}", account.getSchemaName());
            throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
        }

        String schema = account.getSchemaName();

        // 🔥 BIND ANTES DA TX
        TenantContext.bindTenant(schema);
        log.info("🔄 [listTenantUsers] Tenant bind | schema={} | tenantNow={}", schema, TenantContext.getCurrentTenant());

        try {
            List<TenantUserResponse> resp = listTenantUsersTx(account.getId(), onlyActive);
            log.info("✅ [listTenantUsers] OK | accountId={} | schema={} | total={}",
                    account.getId(), schema, resp.size());
            return resp;

        } catch (Exception e) {
            log.error("💥 [listTenantUsers] ERRO | accountId={} | schema={}", accountId, schema, e);
            throw e;
        } finally {
            TenantContext.unbindTenant();
            log.debug("🧹 [listTenantUsers] Tenant unbind (finally) | tenantNow={}", TenantContext.getCurrentTenant());
        }
    }

    @Transactional(readOnly = true)
    protected List<TenantUserResponse> listTenantUsersTx(Long accountId, boolean onlyActive) {

        log.info("🧪 [listTenantUsersTx] TX START | tenant={} | accountId={} | onlyActive={}",
                TenantContext.getCurrentTenant(), accountId, onlyActive);

        List<UserTenant> users = onlyActive
                ? userTenantRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId)
                : userTenantRepository.findByAccountId(accountId);

        log.info("📊 [listTenantUsersTx] Users carregados | count={} | tenant={}",
                users.size(), TenantContext.getCurrentTenant());

        return users.stream().map(TenantUserResponse::from).toList();
    }

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, StatusRequest statusReq) {

        log.info("🔁 [changeAccountStatus] INÍCIO | accountId={} | newStatus={} | reason={} | tenantBefore={}",
                accountId, statusReq.status(), statusReq.reason(), TenantContext.getCurrentTenant());

        TenantContext.unbindTenant();
        log.debug("🧹 [changeAccountStatus] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.warn("❌ [changeAccountStatus] ACCOUNT_NOT_FOUND | accountId={}", accountId);
                    return new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
                });

        log.debug("📦 [changeAccountStatus] Account carregada | accountId={} | schema={} | status={} | system={}",
                account.getId(), account.getSchemaName(), account.getStatus(), account.isSystemAccount());

        if (account.isSystemAccount()) {
            log.warn("⛔ [changeAccountStatus] SYSTEM_ACCOUNT_PROTECTED | accountId={}", accountId);
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
                    "Contas do sistema não podem ter seu status alterado", 403);
        }

        AccountStatus accountCurrentStatus = account.getStatus();
        AccountStatus accountNewStatus = statusReq.status();

        if (accountCurrentStatus == accountNewStatus) {
            log.info("↩️ [changeAccountStatus] Status já aplicado | accountId={} | status={}",
                    accountId, accountCurrentStatus);
            return buildResponse(account, accountCurrentStatus, false, 0);
        }

        if (accountCurrentStatus == AccountStatus.CANCELLED) {
            log.warn("❌ [changeAccountStatus] INVALID | account CANCELLED | accountId={}", accountId);
            throw new ApiException("ACCOUNT_CANCELLED", "Conta cancelada não pode ter status alterado", 409);
        }

        if (accountNewStatus == AccountStatus.FREE_TRIAL && accountCurrentStatus != AccountStatus.FREE_TRIAL) {
            log.warn("❌ [changeAccountStatus] INVALID_STATUS_TRANSITION | {} -> {} | accountId={}",
                    accountCurrentStatus, accountNewStatus, accountId);
            throw new ApiException("INVALID_STATUS_TRANSITION", "Não é permitido voltar para FREE_TRIAL", 409);
        }

        log.info("🔄 [changeAccountStatus] Alterando status | {} → {} | accountId={}",
                accountCurrentStatus, accountNewStatus, accountId);

        account.setStatus(accountNewStatus);

        if (accountNewStatus == AccountStatus.ACTIVE) {
            account.setDeletedAt(null);
            log.debug("♻️ [changeAccountStatus] ACTIVE: limpando deletedAt | accountId={}", accountId);
        }

        accountRepository.save(account);
        log.info("💾 [changeAccountStatus] Conta salva em PUBLIC | accountId={} | statusNow={}",
                accountId, account.getStatus());

        boolean tenantUsersSuspended = false;
        int tenantUsersCount = 0;

        if (accountNewStatus == AccountStatus.SUSPENDED) {
            log.info("⏸️ [changeAccountStatus] Suspendendo users do tenant | accountId={} | schema={}",
                    accountId, account.getSchemaName());
            tenantUsersCount = suspendTenantUsersTx(account);
            tenantUsersSuspended = true;
        }

        if (accountNewStatus == AccountStatus.CANCELLED) {
            log.info("🛑 [changeAccountStatus] Cancelando users do tenant | accountId={} | schema={}",
                    accountId, account.getSchemaName());
            tenantUsersCount = cancelAccountTx(account);
            tenantUsersSuspended = true;
        }

        log.info("✅ [changeAccountStatus] FINALIZADO | accountId={} | prev={} | now={} | tenantUsersAffected={}",
                accountId, accountCurrentStatus, accountNewStatus, tenantUsersCount);

        return buildResponse(account, accountCurrentStatus, tenantUsersSuspended, tenantUsersCount);
    }

    private AccountStatusChangeResponse buildResponse(Account account, AccountStatus previousStatus,
                                                     boolean tenantUsersSuspended, int tenantUsersCount) {
        log.debug("🧾 [buildResponse] accountId={} | prev={} | now={} | schema={} | tenantUsersSuspended={} | count={}",
                account.getId(), previousStatus, account.getStatus(), account.getSchemaName(),
                tenantUsersSuspended, tenantUsersCount);

        return new AccountStatusChangeResponse(
                account.getId(),
                account.getStatus().name(),
                previousStatus.name(),
                LocalDateTime.now(),
                account.getSchemaName(),
                new AccountStatusChangeResponse.SideEffects(tenantUsersSuspended, tenantUsersCount)
        );
    }

    public int cancelAccount(Account account) {
        String tenantSchema = account.getSchemaName();

        log.info("🛑 [cancelAccount] INÍCIO | accountId={} | schema={} | tenantBefore={}",
                account.getId(), tenantSchema, TenantContext.getCurrentTenant());

        // ✅ 1) SALVA PUBLIC (sem tenant)
        TenantContext.unbindTenant();
        log.debug("🧹 [cancelAccount] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        account.setDeletedAt(LocalDateTime.now());
        accountRepository.save(account);
        log.info("💾 [cancelAccount] Account marcada como deletada em PUBLIC | accountId={} | deletedAt={}",
                account.getId(), account.getDeletedAt());

        // ✅ 2) Se tenant não existe, acabou
        boolean schemaOk = validateTenantSchema(tenantSchema);
        boolean usersTableOk = schemaOk && tableExistsInTenant(tenantSchema, "users_tenant");

        log.info("🔎 [cancelAccount] Checks | schemaOk={} | usersTableOk={} | schema={}",
                schemaOk, usersTableOk, tenantSchema);

        if (!schemaOk || !usersTableOk) {
            log.warn("⚠️ [cancelAccount] Cancelamento apenas PUBLIC | schema inválido ou tabela ausente | schema={}",
                    tenantSchema);
            return 0;
        }

        // ✅ 3) Agora sim entra no tenant e remove usuários
        TenantContext.bindTenant(tenantSchema);
        log.info("🔄 [cancelAccount] Tenant bind | schema={} | tenantNow={}", tenantSchema, TenantContext.getCurrentTenant());

        try {
            int removed = cancelAccountTx(account);
            log.info("✅ [cancelAccount] OK | accountId={} | schema={} | usersAffected={}",
                    account.getId(), tenantSchema, removed);
            return removed;
        } catch (Exception e) {
            log.error("💥 [cancelAccount] ERRO | accountId={} | schema={}", account.getId(), tenantSchema, e);
            throw e;
        } finally {
            TenantContext.unbindTenant();
            log.debug("🧹 [cancelAccount] Tenant unbind (finally) | tenantNow={}", TenantContext.getCurrentTenant());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelAccountTx(Account account) {
        log.info("🧪 [cancelAccountTx] TX START | tenant={} | accountId={}",
                TenantContext.getCurrentTenant(), account.getId());

        List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
        log.info("📦 [cancelAccountTx] Users encontrados | count={} | accountId={}",
                users.size(), account.getId());

        users.forEach(UserTenant::softDelete);
        userTenantRepository.saveAll(users);

        log.info("✅ [cancelAccountTx] Usuários cancelados (softDelete) | count={} | tenant={}",
                users.size(), TenantContext.getCurrentTenant());

        return users.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int suspendTenantUsersTx(Account account) {

        String tenantSchema = account.getSchemaName();

        log.info("⏸️ [suspendTenantUsersTx] INÍCIO | accountId={} | schema={} | tenantBefore={}",
                account.getId(), tenantSchema, TenantContext.getCurrentTenant());

        if ("public".equals(tenantSchema)) {
            log.error("❌ [suspendTenantUsersTx] ERRO CRÍTICO: schema public | accountId={}", account.getId());
            return 0;
        }

        if (!validateTenantSchema(tenantSchema)) {
            log.warn("⚠️ [suspendTenantUsersTx] Schema inexistente | {}", tenantSchema);
            return 0;
        }

        if (!tableExistsInTenant(tenantSchema, "users_tenant")) {
            log.warn("⚠️ [suspendTenantUsersTx] Tabela users_tenant inexistente | {}", tenantSchema);
            return 0;
        }

        TenantContext.bindTenant(tenantSchema);
        log.info("🔄 [suspendTenantUsersTx] Tenant bind | schema={} | tenantNow={}",
                tenantSchema, TenantContext.getCurrentTenant());

        try {
            List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
            log.info("📊 [suspendTenantUsersTx] Usuários encontrados | count={} | schema={}",
                    users.size(), tenantSchema);

            users.forEach(u -> u.setActive(false));
            userTenantRepository.saveAll(users);

            log.info("✅ [suspendTenantUsersTx] Usuários suspensos com sucesso | schema={} | count={}",
                    tenantSchema, users.size());

            return users.size();

        } catch (Exception e) {
            log.error("💥 [suspendTenantUsersTx] ERRO | accountId={} | schema={}", account.getId(), tenantSchema, e);
            return 0;
        } finally {
            TenantContext.unbindTenant();
            log.debug("🧹 [suspendTenantUsersTx] Tenant unbind (finally) | tenantNow={}", TenantContext.getCurrentTenant());
        }
    }

    // 🔥 NOVO MÉTODO: Verifica se uma tabela existe no tenant
    private boolean tableExistsInTenant(String schemaName, String tableName) {
        try {
            String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.tables " +
                    "WHERE table_schema = ? AND table_name = ?)";
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName);
            boolean ok = Boolean.TRUE.equals(exists);
            log.debug("🧩 [tableExistsInTenant] schema={} | table={} | exists={}", schemaName, tableName, ok);
            return ok;
        } catch (Exception e) {
            log.error("💥 [tableExistsInTenant] Erro ao verificar tabela {} no schema {}: {}",
                    tableName, schemaName, e.getMessage(), e);
            return false;
        }
    }

    public boolean validateTenantSchema(String schemaName) {
        if ("public".equals(schemaName)) {
            log.debug("🚫 [validateTenantSchema] schema=public => false");
            return false;
        }

        try {
            String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName);
            boolean ok = Boolean.TRUE.equals(exists);
            log.debug("🧩 [validateTenantSchema] schema={} | exists={}", schemaName, ok);
            return ok;
        } catch (Exception e) {
            log.error("💥 [validateTenantSchema] Erro ao verificar schema {}: {}", schemaName, e.getMessage(), e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountDetails(Long accountId) {
        log.info("🔎 [getAccountDetails] INÍCIO | accountId={} | tenantBefore={}",
                accountId, TenantContext.getCurrentTenant());

        TenantContext.unbindTenant();
        log.debug("🧹 [getAccountDetails] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> {
                    log.warn("❌ [getAccountDetails] ACCOUNT_NOT_FOUND | accountId={}", accountId);
                    return new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
                });

        log.info("✅ [getAccountDetails] OK | accountId={} | schema={} | status={}",
                account.getId(), account.getSchemaName(), account.getStatus());

        return mapToResponse(account);
    }

    private AccountResponse mapToResponse(Account account) {
        log.debug("🧱 [mapToResponse] accountId={} | schema={}", account.getId(), account.getSchemaName());
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .schemaName(account.getSchemaName())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .trialEndDate(account.getTrialEndDate())
                .systemAccount(account.isSystemAccount())
                .admin(null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccounts() {
        log.info("📃 [listAllAccounts] INÍCIO | tenantBefore={}", TenantContext.getCurrentTenant());
        TenantContext.unbindTenant();
        log.debug("🧹 [listAllAccounts] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        List<AccountResponse> res = accountRepository.findAllByDeletedFalse()
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();

        log.info("✅ [listAllAccounts] OK | total={}", res.size());
        return res;
    }

    public AccountResponse createAccount(AccountCreateRequest request) {
        log.info("🚀 [createAccount] INÍCIO | name={} | companyEmail={} | tenantBefore={}",
                request.name(), request.companyEmail(), TenantContext.getCurrentTenant());

        TenantContext.unbindTenant(); // PUBLIC
        log.debug("🧹 [createAccount] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = createAccountTx(request); // salva em PUBLIC
        log.info("💾 [createAccount] Account criada em PUBLIC | accountId={} | schema={} | slug={}",
                account.getId(), account.getSchemaName(), account.getSlug());

        try {
            // TENANT: bind + migrate (agora o migrate não desbinda)
            migrateTenant(account);

            log.info("👤 [createAccount] Criando TENANT_ADMIN via JPA | accountId={} | tenantNow={}",
                    account.getId(), TenantContext.getCurrentTenant());

            // ✅ agora o tenant ainda está bindado: JPA vai salvar no schema correto
            UserTenant admin = createTenantAdminJpa(account.getId(), request.admin());

            log.info("✅ [createAccount] Admin criado | userId={} | username={} | accountId={} | tenantNow={}",
                    admin.getId(), admin.getUsername(), admin.getAccountId(), TenantContext.getCurrentTenant());

            log.info("✅ [createAccount] FINALIZADO | AccountId={}", account.getId());
            return mapToResponse(account);

        } catch (Exception e) {
            log.error("💥 [createAccount] ERRO | name={} | schema={}", request.name(), account.getSchemaName(), e);
            throw e;
        } finally {
            TenantContext.unbindTenant(); // <- desbinda uma vez, no final
            log.debug("🧹 [createAccount] Tenant unbind (finally) | tenantNow={}", TenantContext.getCurrentTenant());
        }
    }

    @Transactional
    protected UserTenant createTenantAdminJpa(Long accountId, AdminCreateRequest adminReq) {

        log.info("👤 [createTenantAdminJpa] INÍCIO | accountId={} | username={} | email={} | tenantNow={}",
                accountId, adminReq.username(), adminReq.email(), TenantContext.getCurrentTenant());

        boolean usernameExists = userTenantRepository.existsByUsernameAndAccountId(adminReq.username(), accountId);
        boolean emailExists = userTenantRepository.existsByEmailAndAccountId(adminReq.email(), accountId);

        log.debug("🔎 [createTenantAdminJpa] Duplicidade | usernameExists={} | emailExists={} | accountId={}",
                usernameExists, emailExists, accountId);

        if (usernameExists) {
            log.warn("❌ [createTenantAdminJpa] ADMIN_EXISTS username | accountId={} | username={}",
                    accountId, adminReq.username());
            throw new ApiException("ADMIN_EXISTS", "Já existe usuário com este username", 409);
        }
        if (emailExists) {
            log.warn("❌ [createTenantAdminJpa] ADMIN_EXISTS email | accountId={} | email={}",
                    accountId, adminReq.email());
            throw new ApiException("ADMIN_EXISTS", "Já existe usuário com este email", 409);
        }

        UserTenant u = new UserTenant();
        u.setAccountId(accountId);
        u.setName("Administrador");
        u.setUsername(adminReq.username());
        u.setEmail(adminReq.email());

        // IMPORTANTE: encode a senha aqui (se já tiver PasswordEncoder no projeto)
        u.setPassword(adminReq.password());

        u.setRole(UserTenantRole.TENANT_ADMIN);
        u.setActive(true);
        u.setCreatedAt(LocalDateTime.now());
        u.setTimezone("America/Sao_Paulo");
        u.setLocale("pt_BR");

        log.debug("💾 [createTenantAdminJpa] Salvando UserTenant | accountId={} | role={} | tenantNow={}",
                accountId, u.getRole(), TenantContext.getCurrentTenant());

        UserTenant saved = userTenantRepository.save(u);

        log.info("✅ [createTenantAdminJpa] OK | userId={} | accountId={} | role={} | tenantNow={}",
                saved.getId(), saved.getAccountId(), saved.getRole(), TenantContext.getCurrentTenant());

        return saved;
    }

    /**
     * Verifica se o fluxo de criação está funcionando
     */
    public boolean testTenantCreation(String schemaName) {
        log.info("🧪 [testTenantCreation] INÍCIO | schema={}", schemaName);
        try {
            String sql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
            List<String> schemas = jdbcTemplate.queryForList(sql, String.class, schemaName);
            boolean ok = !schemas.isEmpty();
            log.info("✅ [testTenantCreation] schema={} | ok={}", schemaName, ok);
            return ok;
        } catch (Exception e) {
            log.error("💥 [testTenantCreation] Falhou | schema={} | msg={}", schemaName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Método de recuperação: cria admin se o schema existir mas não tiver admin
     */
    public UserTenant recoverTenantAdmin(Long accountId, String schemaName, AdminCreateRequest adminReq) {
        log.warn("⚠️ [recoverTenantAdmin] INÍCIO | accountId={} | schema={} | username={}",
                accountId, schemaName, adminReq.username());

        if (!tenantSchemaService.isSchemaReady(schemaName)) {
            log.error("❌ [recoverTenantAdmin] SCHEMA_NOT_READY | schema={}", schemaName);
            throw new ApiException("SCHEMA_NOT_READY", "Schema do tenant não está pronto para uso", 500);
        }

        UserTenant admin = tenantSchemaService.createTenantAdmin(accountId, schemaName, adminReq);

        log.info("✅ [recoverTenantAdmin] OK | userId={} | accountId={} | schema={}",
                admin.getId(), accountId, schemaName);

        return admin;
    }

    /*
     * ========================= ACCOUNT (PUBLIC) =========================
     */

    @Transactional
    protected Account createAccountTx(AccountCreateRequest request) {

        log.info("🏗️ [createAccountTx] INÍCIO (PUBLIC) | name={} | tenantNow={}",
                request.name(), TenantContext.getCurrentTenant());

        TenantContext.unbindTenant();
        log.debug("🧹 [createAccountTx] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = Account.builder()
                .name(request.name())
                .schemaName(generateSchemaName(request.name()))
                .slug(generateSlug(request.name()))
                .companyEmail(request.companyEmail())
                .companyDocument(request.companyDocument())
                .createdAt(LocalDateTime.now())
                .trialEndDate(LocalDateTime.now().plusDays(30))
                .status(AccountStatus.FREE_TRIAL)
                .systemAccount(false)
                .build();

        Account saved = accountRepository.save(account);

        log.info("✅ [createAccountTx] OK (PUBLIC) | accountId={} | schema={} | slug={}",
                saved.getId(), saved.getSchemaName(), saved.getSlug());

        return saved;
    }

    protected void migrateTenant(Account account) {
        String schemaName = account.getSchemaName();
        log.info("🏗️ [migrateTenant] INÍCIO | accountId={} | schema={} | tenantBefore={}",
                account.getId(), schemaName, TenantContext.getCurrentTenant());

        TenantContext.bindTenant(schemaName);
        log.info("🔄 [migrateTenant] Tenant bind | schema={} | tenantNow={}", schemaName, TenantContext.getCurrentTenant());

        boolean schemaExists = validateTenantSchema(schemaName);
        log.info("🔎 [migrateTenant] Schema exists? {} | schema={}", schemaExists, schemaName);

        if (!schemaExists) {
            log.warn("📦 [migrateTenant] Criando schema {}", schemaName);
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");

            log.info("✅ [migrateTenant] Schema criado/confirmado | schema={}", schemaName);
        }

        try {
            log.info("🧬 [migrateTenant] Rodando migrations | schema={}", schemaName);
            tenantMigrationService.migrateTenant(schemaName);
            log.info("✅ [migrateTenant] Tenant migrado com sucesso | schema={}", schemaName);
        } catch (Exception e) {
            log.error("💥 [migrateTenant] ERRO migrations | schema={}", schemaName, e);
            throw e;
        }
    }

    @Transactional
    public void softDeleteAccount(Long accountId) {
        log.info("🗑️ [softDeleteAccount] INÍCIO | accountId={}", accountId);

        Account account = getAccountById(accountId);

        if (account.isSystemAccount()) {
            log.warn("⛔ [softDeleteAccount] SYSTEM_ACCOUNT_PROTECTED | accountId={}", accountId);
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", "Não é permitido excluir contas do sistema", 403);
        }

        softDeleteAccountTx(accountId); // public
        softDeleteTenantUsersTx(accountId); // tenant

        log.info("✅ [softDeleteAccount] FINALIZADO | accountId={}", accountId);
    }

    @Transactional
    protected void softDeleteAccountTx(Long accountId) {
        log.info("🧪 [softDeleteAccountTx] TX START (PUBLIC) | accountId={}", accountId);

        TenantContext.unbindTenant();
        log.debug("🧹 [softDeleteAccountTx] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = getAccountById(accountId);
        account.softDelete();
        accountRepository.save(account);

        log.info("✅ [softDeleteAccountTx] OK (PUBLIC) | accountId={} | deleted=true", accountId);
    }

    @Transactional
    protected void softDeleteTenantUsersTx(Long accountId) {
        log.info("🧪 [softDeleteTenantUsersTx] TX START (TENANT) | accountId={}", accountId);

        Account account = getAccountById(accountId);

        TenantContext.bindTenant(account.getSchemaName());
        log.info("🔄 [softDeleteTenantUsersTx] Tenant bind | schema={} | tenantNow={}",
                account.getSchemaName(), TenantContext.getCurrentTenant());

        try {
            List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
            log.info("📦 [softDeleteTenantUsersTx] Users encontrados | count={}", users.size());

            users.forEach(u -> {
                if (!u.isDeleted()) u.softDelete();
            });
            userTenantRepository.saveAll(users);

            log.info("✅ [softDeleteTenantUsersTx] OK | usersAffected={}", users.size());
        } finally {
            TenantContext.unbindTenant();
            log.debug("🧹 [softDeleteTenantUsersTx] Tenant unbind (finally) | tenantNow={}", TenantContext.getCurrentTenant());
        }
    }

    public void restoreAccount(Long accountId) {
        log.info("♻️ [restoreAccount] INÍCIO | accountId={}", accountId);

        Account account = getAccountById(accountId);

        if (account.isSystemAccount() && account.isDeleted()) {
            log.warn("⛔ [restoreAccount] SYSTEM_ACCOUNT_PROTECTED | accountId={}", accountId);
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
                    "Contas do sistema não podem ser restauradas via este endpoint", 403);
        }

        restoreAccountTx(accountId); // public
        restoreTenantUsersTx(accountId); // tenant

        log.info("✅ [restoreAccount] FINALIZADO | accountId={}", accountId);
    }

    @Transactional
    protected void restoreAccountTx(Long accountId) {
        log.info("🧪 [restoreAccountTx] TX START (PUBLIC) | accountId={}", accountId);

        TenantContext.unbindTenant();
        log.debug("🧹 [restoreAccountTx] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = getAccountById(accountId);
        account.restore();
        accountRepository.save(account);

        log.info("✅ [restoreAccountTx] OK (PUBLIC) | accountId={} | deleted=false", accountId);
    }

    @Transactional
    protected void restoreTenantUsersTx(Long accountId) {
        log.info("🧪 [restoreTenantUsersTx] TX START (TENANT) | accountId={}", accountId);

        Account account = getAccountById(accountId);

        TenantContext.bindTenant(account.getSchemaName());
        log.info("🔄 [restoreTenantUsersTx] Tenant bind | schema={} | tenantNow={}",
                account.getSchemaName(), TenantContext.getCurrentTenant());

        try {
            List<UserTenant> users = userTenantRepository.findByAccountId(account.getId());
            log.info("📦 [restoreTenantUsersTx] Users encontrados | count={}", users.size());

            users.forEach(u -> {
                if (u.isDeleted()) u.restore();
            });
            userTenantRepository.saveAll(users);

            log.info("✅ [restoreTenantUsersTx] OK | usersAffected={}", users.size());
        } finally {
            TenantContext.unbindTenant();
            log.debug("🧹 [restoreTenantUsersTx] Tenant unbind (finally) | tenantNow={}", TenantContext.getCurrentTenant());
        }
    }

    /*
     * ========================= AUXILIARES =========================
     */

    @Transactional(readOnly = true)
    public Account getAccountById(Long accountId) {
        log.debug("🔎 [getAccountById] Buscando | accountId={} | tenantNow={}", accountId, TenantContext.getCurrentTenant());
        return accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.warn("❌ [getAccountById] ACCOUNT_NOT_FOUND | accountId={}", accountId);
                    return new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
                });
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String slug = base;
        int i = 1;

        log.debug("🏷️ [generateSlug] base={}", base);

        while (accountRepository.findBySlugAndDeletedFalse(slug).isPresent()) {
            slug = base + "-" + i++;
            log.debug("🏷️ [generateSlug] conflito, tentando slug={}", slug);
        }
        return slug;
    }

    private String generateSchemaName(String name) {
        String schema = "tenant_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_") + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        log.debug("🏗️ [generateSchemaName] name={} => schema={}", name, schema);
        return schema;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccountsWithAdmin() {
        log.info("📃 [listAllAccountsWithAdmin] INÍCIO | tenantBefore={}", TenantContext.getCurrentTenant());
        TenantContext.unbindTenant();

        List<AccountResponse> res = accountRepository.findAllByDeletedFalse().stream()
                .map(this::mapToResponse)
                .toList();

        log.info("✅ [listAllAccountsWithAdmin] OK | total={}", res.size());
        return res;
    }

    @Transactional(readOnly = true)
    public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {

        log.info("🧾 [getAccountAdminDetails] INÍCIO | accountId={} | tenantBefore={}",
                accountId, TenantContext.getCurrentTenant());

        TenantContext.unbindTenant(); // PUBLIC
        log.debug("🧹 [getAccountAdminDetails] Tenant unbind (PUBLIC) | tenantNow={}", TenantContext.getCurrentTenant());

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.warn("❌ [getAccountAdminDetails] ACCOUNT_NOT_FOUND | accountId={}", accountId);
                    return new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
                });

        boolean inTrial = account.getStatus() == AccountStatus.FREE_TRIAL;
        boolean trialExpired = inTrial && account.getTrialEndDate().isBefore(LocalDateTime.now());

        long daysRemaining = inTrial
                ? Math.max(0, java.time.Duration.between(LocalDateTime.now(), account.getTrialEndDate()).toDays())
                : 0;

        log.debug("📌 [getAccountAdminDetails] status={} | inTrial={} | trialExpired={} | daysRemaining={}",
                account.getStatus(), inTrial, trialExpired, daysRemaining);

        AdminUserResponse admin = null;
        long totalUsers = 0;

        log.info("✅ [getAccountAdminDetails] OK | accountId={} | schema={}",
                account.getId(), account.getSchemaName());

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

                admin,
                totalUsers,
                !account.isDeleted()
        );
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByIdWithAdmin(Long accountId) {

        log.info("🔎 [getAccountByIdWithAdmin] INÍCIO | accountId={} | tenantBefore={}",
                accountId, TenantContext.getCurrentTenant());

        TenantContext.unbindTenant();

        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> {
                    log.warn("❌ [getAccountByIdWithAdmin] ACCOUNT_NOT_FOUND | accountId={}", accountId);
                    return new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
                });

        log.info("✅ [getAccountByIdWithAdmin] OK | accountId={} | schema={}",
                account.getId(), account.getSchemaName());

        return mapToResponse(account);
    }
}
