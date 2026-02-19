package brito.com.multitenancy001.integration.auth;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Integração: ControlPlane -> Infrastructure (introspecção do refresh token).
 *
 * Regra:
 * - controlplane.* não importa infrastructure.*
 * - integração encapsula JwtTokenProvider
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneRefreshTokenIntrospectionIntegrationService {

    private final JwtTokenProvider jwtTokenProvider;

    public ControlPlaneRefreshIdentity parseOrThrow(String refreshToken) {
        // Comentário: valida refresh token e extrai identidade mínima
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(refreshToken);
        if (authDomain != AuthDomain.REFRESH) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        String schema = jwtTokenProvider.getTenantSchemaFromToken(refreshToken);
        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);

        if (!StringUtils.hasText(schema) || !StringUtils.hasText(email) || accountId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        return new ControlPlaneRefreshIdentity(email.trim(), accountId, schema.trim());
    }
}
