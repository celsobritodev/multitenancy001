package brito.com.multitenancy001.controlplane.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicSchemaVerifier {
    
    private final JdbcTemplate jdbc;
    
    @EventListener(ApplicationReadyEvent.class)
    public void verifyTables() {
        log.info("🔍 Verificando tabelas criadas pelo Flyway...");
        
        try {
            // Apenas VERIFICA, não cria
            Integer accountsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'accounts'", 
                Integer.class
            );
            
            Integer usersCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users_account'", 
                Integer.class
            );
            
            log.info("✅ Verificação OK! Tabelas encontradas: accounts={}, users={}", accountsCount, usersCount);
            
        } catch (Exception e) {
            log.error("⚠️ Aviso na verificação: {}", e.getMessage());
        }
    }
}