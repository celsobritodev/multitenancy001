package brito.com.multitenancy001.integration.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

/**
 * Integração ControlPlane -> Infra (JWT).
 *
 * Regra: controlplane.* NÃO importa infrastructure.security.jwt.*
 *
 * OBS: Esta classe existia no legado com createAccessToken/createRefreshToken(subject).
 * Agora ela expõe métodos compatíveis com o JwtTokenProvider atual.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneTokenIntegrationService {

    private final JwtTokenProvider jwtTokenProvider;

    public String createAccessToken(Authentication authentication, Long accountId, String context) {
        return jwtTokenProvider.generateControlPlaneToken(authentication, accountId, context);
    }

    public String createRefreshToken(String email, String context, Long accountId) {
        return jwtTokenProvider.generateRefreshToken(email, context, accountId);
    }
}
