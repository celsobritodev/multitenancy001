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
        String currentSchema = TenantContext.getOrNull();
        LocalDateTime now = LocalDateTime.now(clock);

        if ("public".equals(currentSchema) || currentSchema == null) {
            ControlPlaneUser user = controlPlaneUserRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND","Usuário account não encontrado",404));

            return new AuthenticatedUserContext(user, "public", now);
        } else {
            TenantUser user = tenantUserRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND","Usuário não encontrado no tenant",404));

            return new AuthenticatedUserContext(user, currentSchema, now);
        }
    }

    public UserDetails loadUserByUsernameAndSchema(String username, String schema) {
        LocalDateTime now = LocalDateTime.now(clock);

        if ("public".equals(schema)) {
            ControlPlaneUser user = controlPlaneUserRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND","Usuário account não encontrado",404));

            return new AuthenticatedUserContext(user, schema, now);
        }

        throw new ApiException("INVALID_OPERATION","Não é possível carregar usuário de tenant sem accountId",400);
    }
}
