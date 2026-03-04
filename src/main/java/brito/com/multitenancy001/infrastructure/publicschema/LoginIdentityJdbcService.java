package brito.com.multitenancy001.infrastructure.publicschema;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginIdentityJdbcService implements LoginIdentityService {

    private final JdbcTemplate jdbcTemplate;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    private static final String SQL_ENSURE_TENANT = """
        insert into public.login_identities (email, subject_type, account_id, subject_id)
        values (?, ?, ?, ?)
        on conflict (email, account_id) where subject_type = 'TENANT_ACCOUNT'
        do update set
            email = excluded.email,
            subject_id = excluded.subject_id
        """;

    @PostConstruct
    public void init() {
        log.info("🚨🚨🚨 LoginIdentityJdbcService FOI INICIALIZADO! 🚨🚨🚨");
    }

    @Override
    public void ensureTenantIdentity(String email, Long accountId) {
        log.info("🚨🚨🚨 ENTRANDO EM ensureTenantIdentity - IMPLEMENTAÇÃO FOI CHAMADA! email={} accountId={}", email, accountId);
        
        if (accountId == null || email == null) {
            log.error("❌ Parâmetros inválidos | email={} accountId={}", email, accountId);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, 
                "email e accountId são obrigatórios para tenant identity", 500);
        }

        try {
            log.info("🔄 Iniciando transação PUBLIC requiresNew | email={} accountId={}", email, accountId);
            
            publicSchemaUnitOfWork.requiresNew(() -> {
                log.info("📝 Executando JDBC update | email={} accountId={}", email, accountId);
                
                try {
                    int rows = jdbcTemplate.update(
                        SQL_ENSURE_TENANT,
                        email,
                        "TENANT_ACCOUNT",
                        accountId,
                        accountId
                    );
                    
                    log.info("✅ JDBC update executado | email={} accountId={} rows={}", email, accountId, rows);
                    return null;
                    
                } catch (DataAccessException e) {
                    log.error("❌ DataAccessException no JDBC | email={} accountId={} | Message={}", 
                            email, accountId, e.getMessage(), e);
                    throw e;
                } catch (Exception e) {
                    log.error("❌ Exceção inesperada no JDBC | email={} accountId={} | Tipo={} | Message={}", 
                            email, accountId, e.getClass().getName(), e.getMessage(), e);
                    throw e;
                }
            });
            
            log.info("✅ ensureTenantIdentity CONCLUÍDO COM SUCESSO | email={} accountId={}", email, accountId);
            
        } catch (DataAccessException e) {
            log.error("❌ DataAccessException em ensureTenantIdentity | email={} accountId={} | Message={}", 
                    email, accountId, e.getMessage(), e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, 
                "Falha no banco de dados ao garantir identidade de login: " + e.getMessage(), 500);
            
        } catch (ApiException e) {
            log.error("❌ ApiException em ensureTenantIdentity | email={} accountId={} | code={} | message={}", 
                    email, accountId, e.getError(), e.getMessage(), e);
            throw e;
            
        } catch (Exception e) {
            log.error("❌ Exceção inesperada em ensureTenantIdentity | email={} accountId={} | Tipo={} | Message={}", 
                    email, accountId, e.getClass().getName(), e.getMessage(), e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, 
                "Erro inesperado ao garantir identidade de login: " + e.getClass().getSimpleName(), 500);
        }
    }

    @Override
    public void ensureControlPlaneIdentity(String email, Long userId) {
        log.info("ensureControlPlaneIdentity chamado | email={} userId={}", email, userId);
        // Implementar se necessário
    }

    @Override
    public void deleteTenantIdentity(String email, Long accountId) {
        log.debug("deleteTenantIdentity chamado | email={} accountId={}", email, accountId);
        // Implementar se necessário
    }

    @Override
    public void deleteControlPlaneIdentityByUserId(Long userId) {
        log.debug("deleteControlPlaneIdentityByUserId chamado | userId={}", userId);
        // Implementar se necessário
    }

    @Override
    public void moveControlPlaneIdentity(Long userId, String newEmail) {
        log.debug("moveControlPlaneIdentity chamado | userId={} newEmail={}", userId, newEmail);
        // Implementar se necessário
    }
}