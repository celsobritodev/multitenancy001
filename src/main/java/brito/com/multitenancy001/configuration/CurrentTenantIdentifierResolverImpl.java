package brito.com.multitenancy001.configuration;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CurrentTenantIdentifierResolverImpl 
        implements CurrentTenantIdentifierResolver<String> { // Adicione <String>

    private static final String DEFAULT_TENANT = "public";
    
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        
        if (StringUtils.hasText(tenant)) {
            log.debug("Resolvendo tenant: {}", tenant);
            return tenant;
        }
        
        log.debug("Nenhum tenant definido, usando padrão: {}", DEFAULT_TENANT);
        return DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Para multi-tenancy, geralmente retornamos false
        // Isso força a criação de nova sessão quando o tenant muda
        return false;
    }
}