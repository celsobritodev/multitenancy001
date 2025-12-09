package brito.com.example.multitenancy001.services;



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
            String sql = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
            jdbcTemplate.execute(sql);
            log.info("Schema criado: {}", schemaName);
        } catch (Exception e) {
            log.error("Erro ao criar schema: {}", e.getMessage());
            throw new RuntimeException("Erro ao criar schema: " + schemaName, e);
        }
    }
    
    public void createTables(String schemaName) {
        try {
            // Tabela de produtos
            String productsTable = String.format(
                "CREATE TABLE IF NOT EXISTS %s.products (" +
                "id VARCHAR(255) PRIMARY KEY, " +
                "name VARCHAR(200) NOT NULL, " +
                "description TEXT, " +
                "sku VARCHAR(100) UNIQUE, " +
                "price NUMERIC(10,2) NOT NULL, " +
                "stock_quantity INTEGER NOT NULL DEFAULT 0, " +
                "min_stock INTEGER, " +
                "max_stock INTEGER, " +
                "cost_price NUMERIC(10,2), " +
                "profit_margin NUMERIC(5,2), " +
                "category VARCHAR(100), " +
                "brand VARCHAR(100), " +
                "weight_kg NUMERIC(8,3), " +
                "dimensions VARCHAR(50), " +
                "barcode VARCHAR(50), " +
                "active BOOLEAN DEFAULT true, " +
                "supplier_id VARCHAR(255), " +
                "created_at TIMESTAMP NOT NULL, " +
                "updated_at TIMESTAMP, " +
                "deleted_at TIMESTAMP, " +
                "deleted BOOLEAN DEFAULT false)", 
                schemaName
            );
            
            // Tabela de fornecedores
            String suppliersTable = String.format(
                "CREATE TABLE IF NOT EXISTS %s.suppliers (" +
                "id VARCHAR(255) PRIMARY KEY, " +
                "name VARCHAR(200) NOT NULL, " +
                "contact_person VARCHAR(100), " +
                "email VARCHAR(150), " +
                "phone VARCHAR(20), " +
                "address TEXT, " +
                "document VARCHAR(20) UNIQUE, " +
                "document_type VARCHAR(10), " +
                "website VARCHAR(200), " +
                "payment_terms VARCHAR(100), " +
                "lead_time_days INTEGER, " +
                "rating NUMERIC(3,2), " +
                "active BOOLEAN DEFAULT true, " +
                "notes TEXT, " +
                "created_at TIMESTAMP NOT NULL, " +
                "updated_at TIMESTAMP, " +
                "deleted_at TIMESTAMP)", 
                schemaName
            );
            
            // Tabela de vendas
            String salesTable = String.format(
                "CREATE TABLE IF NOT EXISTS %s.sales (" +
                "id VARCHAR(255) PRIMARY KEY, " +
                "sale_date TIMESTAMP NOT NULL, " +
                "total_amount NUMERIC(10,2), " +
                "customer_name VARCHAR(255), " +
                "customer_email VARCHAR(255), " +
                "status VARCHAR(50), " +
                "created_at TIMESTAMP NOT NULL, " +
                "updated_at TIMESTAMP)", 
                schemaName
            );
            
            jdbcTemplate.execute(productsTable);
            jdbcTemplate.execute(suppliersTable);
            jdbcTemplate.execute(salesTable);
            
            log.info("Tabelas criadas no schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("Erro ao criar tabelas: {}", e.getMessage());
            throw new RuntimeException("Erro ao criar tabelas no schema: " + schemaName, e);
        }
    }
}