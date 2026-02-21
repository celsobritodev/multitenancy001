// src/main/java/brito/com/multitenancy001/infrastructure/security/userdetails/TenantUserPrincipalFactory.java
package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory para construir TenantUserPrincipal.
 *
 * Regras:
 * - Centraliza criação do principal.
 * - Garante AppClock como dependência única de tempo.
 */
@Component
@RequiredArgsConstructor
public class TenantUserPrincipalFactory {

    private final AppClock appClock;

    public TenantUserPrincipal create(TenantUser user) {
        /* Cria principal do tenant com AppClock. */
        return new TenantUserPrincipal(user, appClock);
    }
}