package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MultiContextUserDetailsService implements UserDetailsService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final TenantUserRepository tenantUserRepository;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Mantém compatibilidade: decide pelo TenantContext atual
        String schema = TenantContext.getOrNull();
        if (schema == null) {
            return loadControlPlaneUser(username);
        }
        // Se alguém bindar "public" por engano, trate como controlplane
        if ("public".equalsIgnoreCase(schema)) {
            return loadControlPlaneUser(username);
        }
        // Aqui não sabemos accountId; mantém fallback antigo
        LocalDateTime now = LocalDateTime.now(clock);
        TenantUser user = tenantUserRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado no tenant", 404));
        return new AuthenticatedUserContext(user, schema, now);
    }
    
    public UserDetails loadControlPlaneUser(String username, Long accountId) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário da controlplane", 400);
        }

        ControlPlaneUser user = controlPlaneUserRepository
                .findByUsernameAndAccount_IdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Usuário controlplane não encontrado para esta conta", 404));

        return new AuthenticatedUserContext(user, "public", now);
    }
 
    

    public UserDetails loadControlPlaneUser(String username) {
        LocalDateTime now = LocalDateTime.now(clock);

        ControlPlaneUser user = controlPlaneUserRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário controlplane não encontrado", 404));

        return new AuthenticatedUserContext(user, "public", now);
    }

    public UserDetails loadTenantUser(String username, Long accountId) {
        String schema = TenantContext.getOrNull();
        if (schema == null || "public".equalsIgnoreCase(schema)) {
            throw new ApiException("TENANT_CONTEXT_REQUIRED",
                    "TenantContext não está bindado para autenticar usuário tenant", 401);
        }

        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário tenant", 400);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        TenantUser user = tenantUserRepository
                .findByUsernameAndAccountIdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado no tenant", 404));

        return new AuthenticatedUserContext(user, schema, now);
    }

}
