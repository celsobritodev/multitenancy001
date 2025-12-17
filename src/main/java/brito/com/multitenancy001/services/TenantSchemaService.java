package brito.com.multitenancy001.services;

import brito.com.multitenancy001.dtos.AdminCreateRequest;
import brito.com.multitenancy001.entities.account.UserRole;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaService {
    
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    
    public UserTenant createTenantAdmin(Long accountId, String schemaName, AdminCreateRequest adminReq) {
        log.info("👨‍💼 [JDBCTEMPLATE] Criando admin: schema={}, account={}", schemaName, accountId);
        
        // 1. Verifica se schema existe
        if (!isSchemaReady(schemaName)) {
            throw new ApiException("SCHEMA_NOT_READY", "Schema não existe", 500);
        }
        
        String username = adminReq.username().toLowerCase().trim();
        String email = adminReq.email();
        
        try {
            // 2. Verifica se usuário já existe
            String checkSql = String.format(
                "SELECT COUNT(*) FROM %s.users_tenant WHERE username = ? AND account_id = ? AND deleted = false",
                schemaName
            );
            
            Long count = jdbcTemplate.queryForObject(checkSql, Long.class, username, accountId);
            
            if (count != null && count > 0) {
                log.warn("⚠️ Usuário já existe: {} no schema {}", username, schemaName);
                throw new ApiException("USERNAME_ALREADY_EXISTS", "Usuário já existe", 409);
            }
            
            log.info("✅ Usuário não existe, prosseguindo...");
            
            // 3. CORREÇÃO: Busca o próximo ID disponível antes de inserir
            String nextIdSql = String.format(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM %s.users_tenant",
                schemaName
            );
            
            Long nextId = jdbcTemplate.queryForObject(nextIdSql, Long.class);
            log.info("📊 Próximo ID disponível: {}", nextId);
            
            // 4. Insere o admin (SOLUÇÃO SIMPLIFICADA - sem KeyHolder)
            String insertSql = String.format(
                """
                INSERT INTO %s.users_tenant (
                    id, name, username, email, password, role, account_id, 
                    active, must_change_password, timezone, locale, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                schemaName
            );
            
            int rowsAffected = jdbcTemplate.update(
                insertSql,
                nextId,
                "Administrador",
                username,
                email,
                passwordEncoder.encode(adminReq.password()),
                "ADMIN",
                accountId,
                true,
                false,
                "America/Sao_Paulo",
                "pt_BR",
                LocalDateTime.now()
            );
            
            if (rowsAffected == 0) {
                throw new ApiException("INSERT_FAILED", "Falha ao inserir usuário", 500);
            }
            
            log.info("✅ Admin criado com sucesso! User ID: {}, Rows affected: {}", nextId, rowsAffected);
            
            // 5. Retorna o objeto
            return UserTenant.builder()
                .id(nextId)
                .name("Administrador")
                .username(username)
                .email(email)
                .password("[PROTECTED]")
                .role(UserRole.ADMIN)
                .accountId(accountId)
                .active(true)
                .mustChangePassword(false)
                .timezone("America/Sao_Paulo")
                .locale("pt_BR")
                .createdAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("❌ ERRO ao criar admin via JdbcTemplate: {}", e.getMessage(), e);
            throw new ApiException("TENANT_ADMIN_CREATION_FAILED", "Falha ao criar admin: " + e.getMessage(), 500);
        }
    }
    
    /**
     * 🔥 VERSÃO ALTERNATIVA: Método que apenas cria, não retorna objeto
     * Use esta se não precisar do objeto criado
     */
    public void createTenantAdminSimple(Long accountId, String schemaName, AdminCreateRequest adminReq) {
        log.info("👨‍💼 [SIMPLE] Criando admin: schema={}, account={}", schemaName, accountId);
        
        if (!isSchemaReady(schemaName)) {
            throw new ApiException("SCHEMA_NOT_READY", "Schema não existe", 500);
        }
        
        String username = adminReq.username().toLowerCase().trim();
        
        try {
            // Verifica se já existe
            String checkSql = String.format(
                "SELECT COUNT(*) FROM %s.users_tenant WHERE username = ? AND account_id = ?",
                schemaName
            );
            
            Long count = jdbcTemplate.queryForObject(checkSql, Long.class, username, accountId);
            
            if (count != null && count > 0) {
                log.info("⚠️ Usuário já existe, ignorando criação: {}", username);
                return; // Já existe, não precisa criar
            }
            
            // Insere simples
            String insertSql = String.format(
                """
                INSERT INTO %s.users_tenant (
                    name, username, email, password, role, account_id, 
                    active, timezone, locale, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                schemaName
            );
            
            int rows = jdbcTemplate.update(
                insertSql,
                "Administrador",
                username,
                adminReq.email(),
                passwordEncoder.encode(adminReq.password()),
                "ADMIN",
                accountId,
                true,
                "America/Sao_Paulo",
                "pt_BR",
                LocalDateTime.now()
            );
            
            log.info("✅ Admin criado: {} (rows: {})", username, rows);
            
        } catch (Exception e) {
            log.error("❌ ERRO simples: {}", e.getMessage());
            throw new ApiException("CREATE_FAILED", e.getMessage(), 500);
        }
    }
    
    public boolean isSchemaReady(String schemaName) {
        try {
            String sql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
            List<String> schemas = jdbcTemplate.queryForList(sql, String.class, schemaName);
            boolean exists = !schemas.isEmpty();
            log.info("🔍 Schema {} existe: {}", schemaName, exists);
            return exists;
        } catch (Exception e) {
            log.error("❌ Erro ao verificar schema: {}", e.getMessage());
            return false;
        }
    }
}