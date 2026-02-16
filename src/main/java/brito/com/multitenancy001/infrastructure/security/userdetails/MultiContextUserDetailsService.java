package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityFailureCode;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MultiContextUserDetailsService implements UserDetailsService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final TenantUserRepository tenantUserRepository;
    private final LoginIdentityResolver loginIdentityResolver;
    private final AppClock appClock;

    private Instant appNow() {
        return appClock.instant();
    }

    private static String normalizeEmailOrThrow(String raw) {
        String normalized = EmailNormalizer.normalizeOrNull(raw);
        if (normalized == null) {
        	throw new UsernameNotFoundException(SecurityFailureCode.INVALID_USER.name());

        }
        return normalized;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String tenantSchema = TenantContext.getOrNull();
        String loginEmail = normalizeEmailOrThrow(email);

        // CONTROL PLANE
        if (tenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
            return loadControlPlaneUserByLoginIdentity(loginEmail);
        }

        // TENANT (tenantSchema já está bindado => tenant pronto)
        Instant issuedAt = appNow();

        TenantUser user = tenantUserRepository
                .findByEmailAndDeletedFalse(loginEmail)
                .orElseThrow(() -> new UsernameNotFoundException(SecurityFailureCode.INVALID_USER.name()));

        var authorities = AuthoritiesFactory.forTenant(user);
        return AuthenticatedUserContext.fromTenantUser(user, tenantSchema, issuedAt, authorities);
    }

    public UserDetails loadControlPlaneUserByLoginIdentity(String email) {
        Instant issuedAt = appNow();

        Long cpUserId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(email);
        if (cpUserId == null) {
        	throw new UsernameNotFoundException(SecurityFailureCode.INVALID_USER.name());

        }

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndDeletedFalse(cpUserId)
                .orElseThrow(() -> new UsernameNotFoundException(SecurityFailureCode.INVALID_USER.name()));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return AuthenticatedUserContext.fromControlPlaneUser(user, Schemas.CONTROL_PLANE, issuedAt, authorities);
    }

    public UserDetails loadControlPlaneUserByEmail(String email, Long accountId) {
        Instant issuedAt = appNow();

        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_REQUIRED,
                    "accountId é obrigatório para autenticar usuário da controlplane",
                    400
            );
        }

        String loginEmail = normalizeEmailOrThrow(email);

        ControlPlaneUser user = controlPlaneUserRepository
                .findByEmailAndAccount_IdAndDeletedFalse(loginEmail, accountId)
                .orElseThrow(() -> new UsernameNotFoundException(SecurityFailureCode.INVALID_USER.name()));

        var authorities = AuthoritiesFactory.forControlPlane(user);
        return AuthenticatedUserContext.fromControlPlaneUser(user, Schemas.CONTROL_PLANE, issuedAt, authorities);
    }

    public UserDetails loadTenantUserByEmail(String email, Long accountId) {
        String tenantSchema = TenantContext.getOrNull();

        if (tenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
            throw new ApiException(
                    ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "TenantContext não está bindado para autenticar usuário tenant",
                    401
            );
        }

        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_REQUIRED,
                    "accountId é obrigatório para autenticar usuário tenant",
                    400
            );
        }

        Instant issuedAt = appNow();
        String loginEmail = normalizeEmailOrThrow(email);

        TenantUser user = tenantUserRepository
                .findByEmailAndAccountIdAndDeletedFalse(loginEmail, accountId)
                .orElseThrow(() -> new UsernameNotFoundException(SecurityFailureCode.INVALID_USER.name()));

        var authorities = AuthoritiesFactory.forTenant(user);
        return AuthenticatedUserContext.fromTenantUser(user, tenantSchema, issuedAt, authorities);
    }
}
