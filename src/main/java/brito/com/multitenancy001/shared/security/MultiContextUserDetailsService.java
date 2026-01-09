package brito.com.multitenancy001.shared.security;



import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.account.ControlPlaneUserRepository;
import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MultiContextUserDetailsService implements UserDetailsService {
    
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final TenantUserRepository tenantUserRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Determinar se estamos buscando no account ou tenant
        String currentSchema = TenantSchemaContext.getCurrentTenantSchema();
        
        if ("public".equals(currentSchema) || currentSchema == null) {
            // Buscar no account
            ControlPlaneUser user = controlPlaneUserRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário account não encontrado",
                            404
                    ));
            
            return new AuthenticatedUserContext(user, "public");
        } else {
            // Buscar no tenant atual
            // Para encontrar o accountId, precisamos de mais contexto
            // Isso geralmente é resolvido durante o login
            TenantUser user = tenantUserRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado no tenant",
                            404
                    ));
            
            return new AuthenticatedUserContext(user, currentSchema);
        }
    }
    
    public UserDetails loadUserByUsernameAndSchema(String username, String schema) {
        if ("public".equals(schema)) {
            ControlPlaneUser user = controlPlaneUserRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário account não encontrado",
                            404
                    ));
            
            return new AuthenticatedUserContext(user, schema);
        } else {
            // Para buscar no tenant específico, precisamos do accountId
            // Isso é melhor tratado no AuthController
            throw new ApiException(
                    "INVALID_OPERATION",
                    "Não é possível carregar usuário de tenant sem accountId",
                    400
            );
        }
    }
}