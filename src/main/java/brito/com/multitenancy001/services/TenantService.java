package brito.com.multitenancy001.services;

import brito.com.multitenancy001.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final JdbcTemplate jdbcTemplate;

    public void createSchema(String schemaName) {
        try {
            if (!schemaName.matches("[a-zA-Z0-9_]+")) {
                throw new IllegalArgumentException("Nome de schema inválido");
            }

            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Schema criado: {}", schemaName);

        } catch (Exception e) {
            log.error("Erro ao criar schema {}: {}", schemaName, e.getMessage());
            throw new ApiException(
                "SCHEMA_CREATION_FAILED",
                "Falha ao criar o schema do tenant.",
                500
            );
        }
    }

    
    
    


}
