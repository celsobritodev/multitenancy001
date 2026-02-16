package brito.com.multitenancy001.integration.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

/**
 * Integração: ControlPlane -> Infrastructure (JWT).
 * ControlPlane não deve importar infrastructure.*.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneJwtIntegrationService {

    private final JwtTokenProvider jwtTokenProvider;

    public String generateControlPlaneToken(Authentication authentication, Long accountId, String schema) {
        return jwtTokenProvider.generateControlPlaneToken(authentication, accountId, schema);
    }

    public String generateRefreshToken(String email, String schema, Long accountId) {
        return jwtTokenProvider.generateRefreshToken(email, schema, accountId);
    }
}
