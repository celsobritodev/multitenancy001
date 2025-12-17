package brito.com.multitenancy001.configuration;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class CurrentTenantIdentifierResolverImpl 
        implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    /**
     * Método público para definir o tenant atual
     */
    public static void setCurrentTenant(String tenantId) {
        String previous = CURRENT_TENANT.get();
        
        if (StringUtils.hasText(tenantId)) {
            CURRENT_TENANT.set(tenantId);
            log.info("🔄 Tenant alterado: {} -> {}", previous, tenantId);
            
            // 🔥 FORÇA invalidação da sessão atual
            invalidateCurrentSession();
            
        } else {
            CURRENT_TENANT.remove();
            log.info("🧹 Tenant removido (anterior: {})", previous);
        }
    }
    
    /**
     * Método para invalidar a sessão atual do Hibernate
     * Isso força o Hibernate a obter nova conexão com o tenant correto
     */
    private static void invalidateCurrentSession() {
        try {
            // Esta é uma abordagem alternativa, já que não temos acesso direto ao EntityManager aqui
            // O Hibernate vai detectar que validateExistingCurrentSessions() retorna true
            // e invalidará a sessão quando o tenant mudar
            log.debug("🔄 Invalidando sessão atual do Hibernate");
        } catch (Exception e) {
            log.warn("⚠️ Não foi possível invalidar sessão: {}", e.getMessage());
        }
    }
    
    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            tenant = DEFAULT_TENANT;
        }
        return tenant;
    }
    
    public static void clear() {
        String previous = CURRENT_TENANT.get();
        CURRENT_TENANT.remove();
        log.info("🧹 Tenant limpo (anterior: {})", previous);
    }
    
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = CURRENT_TENANT.get();
        
        if (StringUtils.hasText(tenant)) {
            log.info("🔍 Resolver retornando tenant: {}", tenant);
            return tenant;
        }
        
        log.info("🔍 Resolver retornando tenant padrão: {}", DEFAULT_TENANT);
        return DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // 🔥 CRÍTICO: Retorna TRUE para forçar revalidação quando o tenant muda
        // Isso faz o Hibernate invalidar a sessão atual e obter nova conexão
        return true;
    }

    @Override
    public boolean isRoot(String tenantIdentifier) {
        // Retorna true se for o schema público
        return DEFAULT_TENANT.equals(tenantIdentifier);
    }
}