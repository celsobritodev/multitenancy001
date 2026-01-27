package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MultiContextUserDetailsService implements UserDetailsService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final TenantUserRepository tenantUserRepository;
    private final AppClock appClock;

    private LocalDateTime now() {
        return appClock.now();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String schemaName = TenantContext.getOrNull();

        if (schemaName == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(schemaName)) {
            return loadControlPlaneUser(username);
        }

        LocalDateTime now = now();

        // ✅ NÃO use ApiException aqui: Spring Security trata "user não encontrado"
        String loginEmail = (username == null ? "" : username.trim().toLowerCase());
        TenantUser user = tenantUserRepository.findByEmailAndDeletedFalse(loginEmail)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

        var authorities = AuthoritiesFactory.forTenant(user);
        return AuthenticatedUserContext.fromTenantUser(user, schemaName, now, authorities);
    }

    public UserDetails loadControlPlaneUser(String username, Long accountId) {
        LocalDateTime now = now();

        if (accountId == null) {
            // ✅ aqui pode continuar ApiException (erro de request/config)
            throw new ApiException(
                    "ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário da controlplane",
                    400
            );
        }

        // ✅ user não encontrado => UsernameNotFoundException
        ControlPlaneUser user = controlPlaneUserRepository
                .findByEmailAndAccount_IdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return AuthenticatedUserContext.fromControlPlaneUser(user, Schemas.CONTROL_PLANE, now, authorities);
    }

    public UserDetails loadControlPlaneUser(String username) {
        LocalDateTime now = now();

        // ✅ user não encontrado => UsernameNotFoundException
        ControlPlaneUser user = controlPlaneUserRepository.findByEmailAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));


        var authorities = AuthoritiesFactory.forControlPlane(user);
        return AuthenticatedUserContext.fromControlPlaneUser(user, Schemas.CONTROL_PLANE, now, authorities);
    }

    public UserDetails loadTenantUser(String username, Long accountId) {
        String schemaName = TenantContext.getOrNull();
        if (schemaName == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(schemaName)) {
            // ✅ aqui pode continuar ApiException (erro de contexto)
            throw new ApiException(
                    "TENANT_CONTEXT_REQUIRED",
                    "TenantContext não está bindado para autenticar usuário tenant",
                    401
            );
        }

        if (accountId == null) {
            // ✅ aqui pode continuar ApiException (erro de request/config)
            throw new ApiException(
                    "ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário tenant",
                    400
            );
        }

        LocalDateTime now = now();

        // ✅ user não encontrado => UsernameNotFoundException
        TenantUser user = tenantUserRepository
                .findByEmailAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));


        var authorities = AuthoritiesFactory.forTenant(user);
        return AuthenticatedUserContext.fromTenantUser(user, schemaName, now, authorities);
    }
}
