package brito.com.multitenancy001.tenant.application.provisioning;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.domain.security.TenantRole;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantSchemaMigrationService tenantSchemaMigrationService;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    
    
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    
   

    

    private void validateSchemaNameOrThrow(String schemaName) {
        if (!StringUtils.hasText(schemaName)) {
            throw new ApiException("INVALID_SCHEMA", "Schema inválido", 400);
        }

        String trimmed = schemaName.trim();

        if ("public".equalsIgnoreCase(trimmed)) {
            throw new ApiException("INVALID_SCHEMA", "Schema 'public' não é permitido", 400);
        }

        if (!SCHEMA_PATTERN.matcher(trimmed).matches()) {
            throw new ApiException(
                    "INVALID_SCHEMA",
                    "Schema inválido: use apenas letras, números e _ (underscore)",
                    400
            );
        }
    }

    

    public boolean schemaExists(String schemaName) {
        if (!StringUtils.hasText(schemaName)) return false;

        String normalized = schemaName.trim();

        if ("public".equalsIgnoreCase(normalized)) return false;

        String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, normalized);
        return Boolean.TRUE.equals(exists);
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

    public void ensureSchemaExistsAndMigrate(String schemaName) {
        validateSchemaNameOrThrow(schemaName);

        String normalized = schemaName.trim().toLowerCase();


        if (!schemaExists(normalized)) {
            log.info("📦 Criando schema {}", normalized);
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + normalized + "\"");
        }

        log.info("🧬 Rodando migrations do tenant: {}", normalized);
        tenantSchemaMigrationService.migrateTenant(normalized);
    }

    /**
     * Deve ser chamado com TenantContext já bindado no schema do tenant
     */
    public TenantUser tenantOwnerBootstrapService(Account account, String username, String email, String rawPassword) {
        String bound = TenantContext.getOrNull();
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
                .role(TenantRole.TENANT_OWNER)
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .timezone(account.getTimezone() != null ? account.getTimezone() : "America/Sao_Paulo")
                .locale(account.getLocale() != null ? account.getLocale() : "pt_BR")
                .build();

        return tenantUserRepository.save(admin);
    }
}
