package brito.com.multitenancy001.integration.auth;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Integração (Control Plane -> Infrastructure): emissão de tokens JWT.
 *
 * <p>Responsabilidade:</p>
 * <ul>
 *   <li>Isolar Control Plane dos detalhes internos do JwtTokenProvider.</li>
 *   <li>Expor métodos de emissão de access/refresh token com contratos explícitos.</li>
 * </ul>
 *
 * <p>Regra importante:</p>
 * <ul>
 *   <li>Quando o {@link Authentication#getPrincipal()} NÃO for {@code AuthenticatedUserContext},
 *       é obrigatório informar o {@code userId/subjectId} ao emitir o access token, para preencher
 *       as claims de identidade do usuário.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneJwtIntegrationService {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Gera Access Token do Control Plane (assinatura "legacy"/compatível).
     *
     * <p>ATENÇÃO:</p>
     * <ul>
     *   <li>Se o principal NÃO for {@code AuthenticatedUserContext}, este método vai falhar com erro claro,
     *       pois não há {@code subjectId} para preencher as claims.</li>
     *   <li>Para fluxos de login (principal geralmente é {@code UserDetails}), use a sobrecarga com {@code userId}.</li>
     * </ul>
     */
    public String generateControlPlaneToken(Authentication authentication, Long accountId, String schema) {
        return jwtTokenProvider.generateControlPlaneToken(authentication, accountId, schema);
    }

    /**
     * Gera Access Token do Control Plane informando explicitamente o subject_id (userId).
     *
     * <p>Use esta sobrecarga quando o {@link Authentication#getPrincipal()} não for
     * {@code AuthenticatedUserContext} (ex.: durante login, quando o Authentication
     * pode carregar um {@code UserDetails}).</p>
     *
     * @param authentication authentication já validado pelo Spring Security
     * @param accountId id da conta (claim)
     * @param schema contexto/tenant schema (claim)
     * @param userId subject_id (controlplane_user.id) para preencher CLAIM_USER_ID
     * @return JWT access token assinado
     */
    public String generateControlPlaneToken(Authentication authentication, Long accountId, String schema, Long userId) {
        return jwtTokenProvider.generateControlPlaneToken(authentication, accountId, schema, userId);
    }

    /**
     * Gera Refresh Token do Control Plane.
     *
     * <p>Refresh token não depende do principal e não precisa de subjectId.</p>
     */
    public String generateRefreshToken(String email, String schema, Long accountId) {
        return jwtTokenProvider.generateRefreshToken(email, schema, accountId);
    }
}