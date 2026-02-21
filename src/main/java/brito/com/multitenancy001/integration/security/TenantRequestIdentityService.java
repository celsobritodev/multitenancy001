package brito.com.multitenancy001.integration.security;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Integração: Tenant (Application) -> Infrastructure (SecurityUtils).
 *
 * Objetivo:
 * - Evitar tenant.* (application) importar infrastructure.security.* diretamente.
 * - Centralizar leitura da identidade do request (accountId, userId, tenantSchema, email).
 *
 * Regras:
 * - Este serviço é "thin": apenas delega para SecurityUtils.
 * - Lança ApiException (UNAUTHENTICATED/INVALID_USER etc.) conforme as regras do SecurityUtils.
 */
@Service
@RequiredArgsConstructor
public class TenantRequestIdentityService {

    private final SecurityUtils securityUtils;

    public Long getCurrentAccountId() {
        /* Lê accountId do principal autenticado (ou lança UNAUTHENTICATED). */
        return securityUtils.getCurrentAccountId();
    }

    public Long getCurrentUserId() {
        /* Lê userId do principal autenticado (ou lança UNAUTHENTICATED). */
        return securityUtils.getCurrentUserId();
    }

    public String getCurrentTenantSchema() {
        /* Lê tenantSchema do principal autenticado (ou lança UNAUTHENTICATED). */
        return securityUtils.getCurrentTenantSchema();
    }

    public String getCurrentEmail() {
        /* Lê email do principal autenticado (ou lança UNAUTHENTICATED). */
        return securityUtils.getAuthenticatedEmail();
    }

    public boolean isAuthenticated() {
        /* Indica se existe principal autenticado no contexto atual. */
        return securityUtils.isAuthenticated();
    }
}