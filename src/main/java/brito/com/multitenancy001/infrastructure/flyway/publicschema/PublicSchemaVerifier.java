package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.db.Schemas;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicSchemaVerifier {
    
    private final JdbcTemplate jdbcTemplate;
    
    @EventListener(ApplicationReadyEvent.class)
    public void verifyTables() {
        log.info("🔍 Verificando tabelas criadas pelo Flyway...");
        
        try {
            // Apenas VERIFICA, não cria
        	Integer accountsCount = jdbcTemplate.queryForObject(
        		    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'accounts'",
        		    Integer.class,
        		    Schemas.CONTROL_PLANE
        		);

        		Integer usersCount = jdbcTemplate.queryForObject(
        		    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'controlplane_users'",
        		    Integer.class,
        		    Schemas.CONTROL_PLANE
        		);    

            
            log.info("✅ Verificação OK! Tabelas encontradas: accounts={}, controlplane_users={}", accountsCount, usersCount);

        } catch (Exception e) {
            log.error("⚠️ Aviso na verificação: {}", e.getMessage());
        }
    }
}
