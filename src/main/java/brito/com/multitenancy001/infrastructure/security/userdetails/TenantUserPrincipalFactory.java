package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory para construção de {@link TenantUserPrincipal}.
 *
 * <p>Responsabilidade:</p>
 * <ul>
 *   <li>Centralizar a criação do principal do tenant.</li>
 *   <li>Garantir uso consistente do {@link AppClock}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantUserPrincipalFactory {

    private final AppClock appClock;

    public TenantUserPrincipal create(TenantUser user) {
        return new TenantUserPrincipal(user, appClock);
    }
}