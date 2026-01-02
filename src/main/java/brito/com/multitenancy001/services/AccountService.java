package brito.com.multitenancy001.services;




import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.TenantUserRepository;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;          // PUBLIC
    private final TenantUserRepository userTenantRepository;    // TENANT
    private final TenantSchemaService tenantSchemaService;

    private final PasswordEncoder passwordEncoder;

    /* =========================================================
       LISTAGEM / GET (PUBLIC)
       ========================================================= */

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
        TenantAccount a = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
        return AccountResponse.fromEntity(a);
    }

    @Transactional(readOnly = true)
    public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {
        TenantContext.unbindTenant();
        TenantAccount a = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        boolean inTrial = a.getStatus() == TenantAccountStatus.FREE_TRIAL;
        boolean trialExpired = inTrial && a.getTrialEndDate() != null && a.getTrialEndDate().isBefore(LocalDateTime.now());
        long daysRemaining = (inTrial && a.getTrialEndDate() != null)
                ? Math.max(0, java.time.Duration.between(LocalDateTime.now(), a.getTrialEndDate()).toDays())
                : 0;

        // Se você quiser, dá pra resolver admin/totalUsers aqui (opcional).
        AdminUserResponse admin = null;
        long totalUsers = 0;

        return new AccountAdminDetailsResponse(
                a.getId(),
                a.getName(),
                a.getSlug(),
                a.getSchemaName(),
                a.getStatus().name(),
                a.getCompanyDocument(),
                a.getCompanyEmail(),
                a.getCreatedAt(),
                a.getTrialEndDate(),
                a.getPaymentDueDate(),
                a.getDeletedAt(),
                inTrial,
                trialExpired,
                daysRemaining,
                admin,
                totalUsers,
                !a.isDeleted()
        );
    }

    /* =========================================================
       CRIAÇÃO DE CONTA (PUBLIC -> TENANT)
       ========================================================= */

    public AccountResponse createAccount(AccountCreateRequest request) {
        TenantContext.unbindTenant(); // garante PUBLIC

        TenantAccount account = createAccountTx(request); // salva em PUBLIC

        try {
            // cria schema + roda migrations do tenant
            migrateTenant(account);

            // cria TENANT_ADMIN no schema do tenant
            TenantContext.bindTenant(account.getSchemaName());
            TenantUser admin = createTenantAdminJpa(account.getId(), request.admin());

            log.info("✅ Account criada | accountId={} | schema={} | slug={} | adminUsername={}",
                    account.getId(), account.getSchemaName(), account.getSlug(), admin.getUsername());

            return AccountResponse.fromEntity(account);

        } catch (Exception e) {
            log.error("💥 Erro criando conta | name={} | schema={}", request.name(), account.getSchemaName(), e);
            throw e;
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional
    protected TenantAccount createAccountTx(AccountCreateRequest request) {
        TenantContext.unbindTenant();

        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String slug = generateSlug(request.name());
            String schemaName = generateSchemaName(request.name());

            try {
            	TenantAccount account = TenantAccount.builder()
                        .name(request.name())
                        .slug(slug)
                        .schemaName(schemaName)
                        .companyEmail(request.companyEmail())
                        .companyDocument(request.companyDocument())
                        .createdAt(LocalDateTime.now())
                        .trialEndDate(LocalDateTime.now().plusDays(30))
                        .status(TenantAccountStatus.FREE_TRIAL)
                        .systemAccount(false)
                        .build();

                return accountRepository.save(account);

            } catch (DataIntegrityViolationException e) {
                if (!isSlugOrSchemaUniqueViolation(e)) throw e;
                log.warn("⚠️ Colisão (tentativa {}/{}) | slug={} | schema={}",
                        attempt, maxAttempts, slug, schemaName);
            }
        }

        throw new ApiException("ACCOUNT_CREATE_FAILED",
                "Não foi possível criar conta (colisão de identificadores). Tente novamente.",
                409);
    }

    @Transactional
    protected TenantUser createTenantAdminJpa(Long accountId, AdminCreateRequest adminReq) {
        if (adminReq == null) {
            throw new ApiException("ADMIN_REQUIRED", "Admin é obrigatório na criação da conta", 400);
        }

        String username = normalizeUsername(adminReq.username());
        String email = (adminReq.email() == null ? null : adminReq.email().trim().toLowerCase());

        boolean usernameExists = userTenantRepository.existsByUsernameAndAccountId(username, accountId);
        boolean emailExists = email != null && userTenantRepository.existsByEmailAndAccountId(email, accountId);

        if (usernameExists) throw new ApiException("ADMIN_EXISTS", "Já existe usuário com este username", 409);
        if (emailExists) throw new ApiException("ADMIN_EXISTS", "Já existe usuário com este email", 409);

        TenantUser u = new TenantUser();
        u.setAccountId(accountId);
        u.setName(StringUtils.hasText(adminReq.name()) ? adminReq.name().trim() : "Administrador");
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(adminReq.password()));
        u.setRole(TenantRole.TENANT_ADMIN);
        u.setActive(true);
        u.setCreatedAt(LocalDateTime.now());
        u.setTimezone("America/Sao_Paulo");
        u.setLocale("pt_BR");

        return userTenantRepository.save(u);
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


    
    
    /* =========================================================
       ADMIN: LISTAR USERS DO TENANT (entra no schema certo)
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

    @Transactional(readOnly = true)
    protected List<TenantUserResponse> listTenantUsersTx(Long accountId, boolean onlyActive) {
        List<TenantUser> users = onlyActive
                ? userTenantRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId)
                : userTenantRepository.findByAccountId(accountId);

        return users.stream().map(TenantUserResponse::from).toList();
    }

    /* =========================================================
       STATUS (PUBLIC + SIDE EFFECTS NO TENANT)
       ========================================================= */

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, StatusRequest req) {
        TenantContext.unbindTenant();

        TenantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        if (account.isSystemAccount()) {
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", "Contas do sistema não podem ter seu status alterado", 403);
        }

        TenantAccountStatus previous = account.getStatus();
        TenantAccountStatus next = req.status();

        if (previous == next) {
            return buildStatusChangeResponse(account, previous, false, 0);
        }

        if (previous == TenantAccountStatus.CANCELLED) {
            throw new ApiException("ACCOUNT_CANCELLED", "Conta cancelada não pode ter status alterado", 409);
        }

        // Regras: não volta para trial
        if (next == TenantAccountStatus.FREE_TRIAL && previous != TenantAccountStatus.FREE_TRIAL) {
            throw new ApiException("INVALID_STATUS_TRANSITION", "Não é permitido voltar para FREE_TRIAL", 409);
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
            affected = cancelAccount(account); // faz PUBLIC + TENANT
            applied = true;
        }

        return buildStatusChangeResponse(account, previous, applied, affected);
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

    /**
     * Cancelamento "completo":
     * - marca deletado em PUBLIC
     * - se schema e tabela existirem, soft delete em users_tenant
     */
    public int cancelAccount(TenantAccount account) {
        String schema = account.getSchemaName();

        // 1) PUBLIC
        TenantContext.unbindTenant();
        account.setDeletedAt(LocalDateTime.now());
        accountRepository.save(account);

        // 2) TENANT (se existir)
        boolean schemaOk = tenantSchemaService.validateTenantSchema(schema);
        boolean tableOk = schemaOk && tenantSchemaService.tableExists(schema, "users_tenant");
        if (!tableOk) return 0;

        TenantContext.bindTenant(schema);
        try {
            return cancelAccountTx(account);
        } finally {
            TenantContext.unbindTenant();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelAccountTx(TenantAccount account) {
        List<TenantUser> users = userTenantRepository.findByAccountId(account.getId());
        users.forEach(TenantUser::softDelete);
        userTenantRepository.saveAll(users);
        return users.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    /* =========================================================
       SOFT DELETE / RESTORE
       ========================================================= */

    @Transactional
    public void softDeleteAccount(Long accountId) {
        TenantContext.unbindTenant();

        TenantAccount account = getAccountById(accountId);

        if (account.isSystemAccount()) {
            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", "Não é permitido excluir contas do sistema", 403);
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

    @Transactional
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

    @Transactional
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

    /* =========================================================
       AUXILIARES
       ========================================================= */

    @Transactional(readOnly = true)
    public TenantAccount getAccountById(Long accountId) {
        TenantContext.unbindTenant();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

   

   
    private String generateSlug(String name) {
        String base = (name == null ? "conta" : name.toLowerCase())
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        String slug = base;
        int i = 1;

        while (accountRepository.findBySlugAndDeletedFalse(slug).isPresent()) {
            slug = base + "-" + (i++);
        }
        return slug;
    }

    private String generateSchemaName(String name) {
        String base = (name == null ? "tenant" : name.toLowerCase())
                .replaceAll("[^a-z0-9]", "_");
        return "tenant_" + base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean isSlugOrSchemaUniqueViolation(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        String msg = (t.getMessage() == null) ? "" : t.getMessage().toLowerCase();

        // ajuste para os nomes reais das constraints do seu banco
        return msg.contains("ux_accounts_slug_active")
                || msg.contains("uk_accounts_slug")
                || msg.contains("uk_accounts_schema_name")
                || msg.contains("accounts_slug_key")
                || msg.contains("accounts_schema_name_key");
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new ApiException("INVALID_USERNAME", "Username é obrigatório", 400);
        }
        return username.trim().toLowerCase();
    }
}
