package brito.com.multitenancy001.integration.security;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;

/**
 * Integração: ControlPlane -> Infrastructure (SecurityUtils).
 * Evita ControlPlane importar infrastructure.security.*.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneRequestIdentityService {

    private final SecurityUtils securityUtils;

    public Long getCurrentAccountId() {
        return securityUtils.getCurrentAccountId();
    }

    public Long getCurrentUserId() {
        return securityUtils.getCurrentUserId();
    }
}
