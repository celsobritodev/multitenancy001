package brito.com.multitenancy001.integration.auth;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Integração ControlPlane -> Infra (JWT).
 *
 * Regras:
 * - controlplane.* NÃO importa infrastructure.security.jwt.*
 * - Esta classe é o "anti-corruption layer" para geração de tokens.
 *
 * Observação importante:
 * - Em alguns fluxos, authentication.getPrincipal() pode vir como UserDetails
 *   (ex.: org.springframework.security.core.userdetails.User), e NÃO como
 *   AuthenticatedUserContext. Nesse caso, JwtTokenProvider exige o userId (subjectId)
 *   explícito para preencher claims (userId/roleName/roleAuthority) de forma segura.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneTokenIntegrationService {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Cria access token do Control Plane.
     *
     * Regra:
     * - Se o principal NÃO for AuthenticatedUserContext, informe subjectId (userId) para evitar ClassCastException.
     */
    public String createAccessToken(Authentication authentication, Long accountId, String context, Long subjectId) {
        /* Delegação explícita com subjectId para suportar principal=UserDetails */
        return jwtTokenProvider.generateControlPlaneToken(authentication, accountId, context, subjectId);
    }

    /**
     * Backward-compatible.
     *
     * Regra:
     * - Mantido para não quebrar callers antigos.
     * - Se o principal não for AuthenticatedUserContext, JwtTokenProvider irá falhar com erro claro,
     *   pois não há subjectId para preencher as claims.
     */
    public String createAccessToken(Authentication authentication, Long accountId, String context) {
        /* Mantém compatibilidade; preferir a assinatura com subjectId */
        return jwtTokenProvider.generateControlPlaneToken(authentication, accountId, context);
    }

    /**
     * Cria refresh token (não precisa de subjectId).
     */
    public String createRefreshToken(String email, String context, Long accountId) {
        /* Refresh token não depende do principal */
        return jwtTokenProvider.generateRefreshToken(email, context, accountId);
    }
}