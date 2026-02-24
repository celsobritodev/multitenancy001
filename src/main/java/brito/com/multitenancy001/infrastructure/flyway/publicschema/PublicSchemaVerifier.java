package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.db.Schemas;

/**
 * Verificador leve pós-startup para confirmar que o Flyway criou
 * tabelas mínimas do PUBLIC schema.
 *
 * <p>Observação: este componente não cria nada — apenas consulta metadados.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicSchemaVerifier {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyTables() {
        log.info("🔍 Verificando tabelas criadas pelo Flyway (schema={})...", Schemas.CONTROL_PLANE);

        try {
            Integer accountsCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " +
                    "  FROM information_schema.tables " +
                    " WHERE table_schema = ? " +
                    "   AND table_name = 'accounts'",
                    Integer.class,
                    Schemas.CONTROL_PLANE
            );

            Integer usersCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " +
                    "  FROM information_schema.tables " +
                    " WHERE table_schema = ? " +
                    "   AND table_name = 'controlplane_users'",
                    Integer.class,
                    Schemas.CONTROL_PLANE
            );

            log.info("✅ Verificação OK! Tabelas encontradas: accounts={}, controlplane_users={}",
                    accountsCount, usersCount);

            if (log.isDebugEnabled()) {
                String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
                String searchPath = jdbcTemplate.queryForObject("show search_path", String.class);
                log.debug("ℹ️ DB context | current_schema={} | search_path={}", currentSchema, searchPath);
            }

        } catch (Exception e) {
            log.error("⚠️ Aviso na verificação do PUBLIC schema: {}", e.getMessage(), e);
        }
    }
}