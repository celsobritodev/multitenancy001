package brito.com.multitenancy001.integration.security;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;

/**
 * Integração ControlPlane -> Infra (segurança).
 *
 * Regra: controlplane.* NÃO importa infrastructure.security.*
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneSecurityContextService {

    private final SecurityUtils securityUtils;

    public Long authenticatedUserId() {
        return securityUtils.getAuthenticatedUserId();
    }

    public String authenticatedEmail() {
        return securityUtils.getAuthenticatedEmail();
    }

    public boolean isAuthenticated() {
        return securityUtils.isAuthenticated();
    }
}
