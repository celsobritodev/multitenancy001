package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
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

        if (schemaName == null || "public".equalsIgnoreCase(schemaName)) {
            return loadControlPlaneUser(username);
        }

        LocalDateTime now = now();

        TenantUser user = tenantUserRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado no tenant", 404));

        var authorities = AuthoritiesFactory.forTenant(user);
        return new AuthenticatedUserContext(user, schemaName, now, authorities);
    }

    public UserDetails loadControlPlaneUser(String username, Long accountId) {
        LocalDateTime now = now();

        if (accountId == null) {
            throw new ApiException(
                    "ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário da controlplane",
                    400
            );
        }

        ControlPlaneUser user = controlPlaneUserRepository
                .findByUsernameAndAccount_IdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário controlplane não encontrado para esta conta",
                        404
                ));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return new AuthenticatedUserContext(user, "public", now, authorities);
    }

    public UserDetails loadControlPlaneUser(String username) {
        LocalDateTime now = now();

        ControlPlaneUser user = controlPlaneUserRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário controlplane não encontrado", 404));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return new AuthenticatedUserContext(user, "public", now, authorities);
    }

    public UserDetails loadTenantUser(String username, Long accountId) {
        String schemaName = TenantContext.getOrNull();
        if (schemaName == null || "public".equalsIgnoreCase(schemaName)) {
            throw new ApiException(
                    "TENANT_CONTEXT_REQUIRED",
                    "TenantContext não está bindado para autenticar usuário tenant",
                    401
            );
        }

        if (accountId == null) {
            throw new ApiException(
                    "ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário tenant",
                    400
            );
        }

        LocalDateTime now = now();

        TenantUser user = tenantUserRepository
                .findByUsernameAndAccountIdAndDeletedFalse(username, accountId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado no tenant", 404));

        var authorities = AuthoritiesFactory.forTenant(user);
        return new AuthenticatedUserContext(user, schemaName, now, authorities);
    }
}
