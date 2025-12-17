package brito.com.multitenancy001.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider
        extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl<String> { // ← ADICIONE <String>

    private static final long serialVersionUID = 1L;

    private final DataSource dataSource;

    @Override
    protected DataSource selectAnyDataSource() {
        return dataSource;
    }

    @Override
    protected DataSource selectDataSource(String tenantIdentifier) { // ← Agora é String, não Object
        return dataSource;
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException { // ← Agora é String
        Connection connection = selectAnyDataSource().getConnection();
        
        try {
            if (StringUtils.hasText(tenantIdentifier)) {
                // Validação de segurança
                validateTenantSchema(tenantIdentifier);
                
                String sql = String.format("SET search_path TO %s, public", tenantIdentifier);
                log.debug("Configurando search_path: {}", sql);
                connection.createStatement().execute(sql);
            } else {
                connection.createStatement().execute("SET search_path TO public");
            }
        } catch (SQLException e) {
            log.error("Erro ao configurar tenant connection", e);
            connection.close();
            throw e;
        }
        
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException { // ← String
        try {
            if (connection != null && !connection.isClosed()) {
                // Reset para schema público
                connection.createStatement().execute("SET search_path TO public");
                log.debug("Conexão resetada para schema público");
            }
        } catch (SQLException e) {
            log.warn("Erro ao resetar search_path", e);
        } finally {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false; // 
    }
    
    private void validateTenantSchema(String schemaName) {
        // Previne SQL Injection
        // Schema names válidos: letras, números, underscore
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Nome de schema inválido: " + schemaName);
        }
    }
}