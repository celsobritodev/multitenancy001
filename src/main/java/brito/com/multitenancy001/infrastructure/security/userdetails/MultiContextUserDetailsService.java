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

    private LocalDateTime now() { return appClock.now(); }

    private static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    private static void assertEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("INVALID_USER");
        }
    }

    /**
     * Spring Security exige essa assinatura, mas aqui tratamos o parâmetro como EMAIL.
     *
     * Regras:
     * - CONTROLPLANE: carrega por email no schema public
     * - TENANT: carrega por email dentro do schema do tenant (TenantContext já está bindado)
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String schemaName = TenantContext.getOrNull();
        String loginEmail = normalizeEmail(email);
        assertEmail(loginEmail);

        // CONTROL PLANE continua normal (email é único globalmente)
        if (schemaName == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(schemaName)) {
            return loadControlPlaneUserByEmail(loginEmail);
        }

        // TENANT: dentro do schema do tenant
        LocalDateTime now = now();

        TenantUser user = tenantUserRepository
                .findByEmailAndDeletedFalse(loginEmail)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

        var authorities = AuthoritiesFactory.forTenant(user);
        return AuthenticatedUserContext.fromTenantUser(user, schemaName, now, authorities);
    }

    public UserDetails loadControlPlaneUserByEmail(String email, Long accountId) {
        LocalDateTime now = now();

        if (accountId == null) {
            throw new ApiException(
                    "ACCOUNT_REQUIRED",
                    "accountId é obrigatório para autenticar usuário da controlplane",
                    400
            );
        }

        String loginEmail = normalizeEmail(email);
        assertEmail(loginEmail);

        ControlPlaneUser user = controlPlaneUserRepository
                .findByEmailAndAccount_IdAndDeletedFalse(loginEmail, accountId)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return AuthenticatedUserContext.fromControlPlaneUser(user, Schemas.CONTROL_PLANE, now, authorities);
    }

    public UserDetails loadControlPlaneUserByEmail(String email) {
        LocalDateTime now = now();

        String loginEmail = normalizeEmail(email);
        assertEmail(loginEmail);

        ControlPlaneUser user = controlPlaneUserRepository.findByEmailAndDeletedFalse(loginEmail)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return AuthenticatedUserContext.fromControlPlaneUser(user, Schemas.CONTROL_PLANE, now, authorities);
    }

    public UserDetails loadTenantUserByEmail(String email, Long accountId) {
        String schemaName = TenantContext.getOrNull();
        if (schemaName == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(schemaName)) {
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

        String loginEmail = normalizeEmail(email);
        assertEmail(loginEmail);

        TenantUser user = tenantUserRepository
                .findByEmailAndAccountIdAndDeletedFalse(loginEmail, accountId)
                .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

        var authorities = AuthoritiesFactory.forTenant(user);
        return AuthenticatedUserContext.fromTenantUser(user, schemaName, now, authorities);
    }
}
