package brito.com.multitenancy001.integration.security;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Integração: Tenant (Application) -> Infrastructure (SecurityUtils).
 *
 * Objetivo:
 * - Evitar tenant.* (application) importar infrastructure.security.* diretamente.
 * - Centralizar leitura da identidade do request (accountId, userId, tenantSchema).
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
}