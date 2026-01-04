package brito.com.multitenancy001.services;

import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.repositories.tenant.TenantUserRepository;
import brito.com.multitenancy001.tenant.domain.user.TenantRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantSchemaMigrationService tenantMigrationService;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean schemaExists(String schemaName) {
        if (!StringUtils.hasText(schemaName) || "public".equals(schemaName)) return false;

        String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName);
        return Boolean.TRUE.equals(exists);
    }

    public boolean validateTenantSchema(String schemaName) {
        return schemaExists(schemaName);
    }

    public boolean tableExists(String schemaName, String tableName) {
        if (!StringUtils.hasText(schemaName) || !StringUtils.hasText(tableName)) return false;

        String sql =
                "SELECT EXISTS(" +
                "  SELECT 1 FROM information_schema.tables " +
                "  WHERE table_schema = ? AND table_name = ?" +
                ")";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName);
        return Boolean.TRUE.equals(exists);
    }

    public void ensureSchemaAndMigrate(String schemaName) {
        if (!StringUtils.hasText(schemaName) || "public".equals(schemaName)) {
            throw new ApiException("INVALID_SCHEMA", "Schema inválido", 400);
        }

        if (!schemaExists(schemaName)) {
            log.info("📦 Criando schema {}", schemaName);
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        }

        log.info("🧬 Rodando migrations do tenant: {}", schemaName);
        tenantMigrationService.migrateTenant(schemaName);
    }

    /**
     * Deve ser chamado com TenantContext já bindado no schema do tenant
     */
    public TenantUser createTenantAdmin(TenantAccount account, String username, String email, String rawPassword) {
        String bound = TenantContext.getCurrentTenant();
        if (bound == null || !bound.equals(account.getSchemaName())) {
            throw new ApiException("TENANT_NOT_BOUND", "Tenant não está bindado no schema esperado", 500);
        }

        String normUsername = username == null ? null : username.trim().toLowerCase();
        String normEmail = email == null ? null : email.trim().toLowerCase();

        if (!StringUtils.hasText(normUsername)) {
            throw new ApiException("INVALID_USERNAME", "Username é obrigatório", 400);
        }
        if (!StringUtils.hasText(normEmail)) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        boolean existsUser = tenantUserRepository.existsByUsernameAndAccountId(normUsername, account.getId());
        boolean existsEmail = tenantUserRepository.existsByEmailAndAccountId(normEmail, account.getId());

        if (existsUser) throw new ApiException("ADMIN_EXISTS", "Já existe usuário com este username", 409);
        if (existsEmail) throw new ApiException("ADMIN_EXISTS", "Já existe usuário com este email", 409);

        TenantUser admin = TenantUser.builder()
                .accountId(account.getId())
                .name("Administrador")
                .username(normUsername)
                .email(normEmail)
                .password(passwordEncoder.encode(rawPassword))
                .role(TenantRole.TENANT_ADMIN)
                .active(true)
                .timezone(account.getTimezone() != null ? account.getTimezone() : "America/Sao_Paulo")
                .locale(account.getLocale() != null ? account.getLocale() : "pt_BR")
                .build();

        return tenantUserRepository.save(admin);
    }
}
