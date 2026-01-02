package brito.com.multitenancy001.services;



import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.user.PlatformUser;
import brito.com.multitenancy001.repositories.TenantUserRepository;
import brito.com.multitenancy001.repositories.UserAccountRepository;
import brito.com.multitenancy001.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserAccountRepository userAccountRepository;
    private final TenantUserRepository userTenantRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Determinar se estamos buscando no account ou tenant
        String currentSchema = TenantContext.getCurrentTenant();
        
        if ("public".equals(currentSchema) || currentSchema == null) {
            // Buscar no account
            PlatformUser user = userAccountRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário account não encontrado",
                            404
                    ));
            
            return new CustomUserDetails(user, "public");
        } else {
            // Buscar no tenant atual
            // Para encontrar o accountId, precisamos de mais contexto
            // Isso geralmente é resolvido durante o login
            TenantUser user = userTenantRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado no tenant",
                            404
                    ));
            
            return new CustomUserDetails(user, currentSchema);
        }
    }
    
    public UserDetails loadUserByUsernameAndSchema(String username, String schema) {
        if ("public".equals(schema)) {
            PlatformUser user = userAccountRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário account não encontrado",
                            404
                    ));
            
            return new CustomUserDetails(user, schema);
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