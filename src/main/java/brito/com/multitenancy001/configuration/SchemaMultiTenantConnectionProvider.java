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
        extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl<String> {

    private static final long serialVersionUID = 1L;
    
    private final DataSource dataSource;

    @Override
    protected DataSource selectAnyDataSource() {
        return dataSource;
    }

    @Override
    protected DataSource selectDataSource(String tenantIdentifier) {
        return dataSource;
    }
    
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
    	
    	
    	log.info("🔍 CHAMADA getConnection() - Thread: {}, Tenant solicitado: {}", 
                Thread.currentThread().threadId(), 
                tenantIdentifier);
    	
    	 log.info("🔍 Tenant no ThreadLocal: {}", 
    	            CurrentTenantIdentifierResolverImpl.getCurrentTenant());
    	
    	
        // 🔥 LOG CRÍTICO PARA DEBUG
        log.info("🔄 [MULTITENANCY] getConnection() chamado para tenant: {}", 
                tenantIdentifier != null ? tenantIdentifier : "null/undefined");
        
        // 🔥 CHAVE DA SOLUÇÃO: Se tenantIdentifier for null, use o DEFAULT
        String effectiveTenant = tenantIdentifier;
        if (!StringUtils.hasText(effectiveTenant)) {
            effectiveTenant = "public";
            log.info("⚠️ [MULTITENANCY] TenantIdentifier vazio, usando: {}", effectiveTenant);
        }
        
        Connection connection = dataSource.getConnection();
        
        try {
            if (!"public".equals(effectiveTenant)) {
                // 🔥 GARANTE que o schema existe
                ensureSchemaExists(connection, effectiveTenant);
                
                // 🔥 CONFIGURA o search_path explicitamente
                String sql = String.format("SET search_path TO %s, public", effectiveTenant);
                log.info("🎯 [MULTITENANCY] Executando: {}", sql);
                connection.createStatement().execute(sql);
                
                log.info("✅ [MULTITENANCY] Conexão configurada para schema: {}", effectiveTenant);
            } else {
                connection.createStatement().execute("SET search_path TO public");
                log.info("🏠 [MULTITENANCY] Conexão configurada para schema público");
            }
            
            return connection;
            
        } catch (SQLException e) {
            log.error("❌ [MULTITENANCY] Erro ao configurar conexão para {}", effectiveTenant, e);
            connection.close();
            throw e;
        }
    }
    
    /**
     * 🔥 GARANTE que o schema existe (idempotente)
     */
    private void ensureSchemaExists(Connection connection, String schemaName) throws SQLException {
        try {
            // Tenta criar o schema (IF NOT EXISTS é idempotente)
            String createSql = String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName);
            log.info("📦 [MULTITENANCY] Criando/verificando schema: {}", schemaName);
            connection.createStatement().execute(createSql);
            
            // Verifica se foi criado
            String checkSql = String.format(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '%s'",
                schemaName
            );
            var rs = connection.createStatement().executeQuery(checkSql);
            if (rs.next()) {
                log.info("✅ [MULTITENANCY] Schema {} está pronto", schemaName);
            } else {
                log.error("❌ [MULTITENANCY] Schema {} NÃO foi criado!", schemaName);
            }
            
        } catch (SQLException e) {
            // Se o schema já existe, apenas log e continue
            if (e.getMessage().contains("already exists")) {
                log.info("📦 [MULTITENANCY] Schema {} já existe", schemaName);
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            if (connection != null && !connection.isClosed()) {
                log.debug("🔌 [MULTITENANCY] Liberando conexão");
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("⚠️ [MULTITENANCY] Erro ao liberar conexão", e);
            throw e;
        }
    }
}