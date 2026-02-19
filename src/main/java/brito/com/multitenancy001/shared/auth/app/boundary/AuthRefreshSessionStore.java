package brito.com.multitenancy001.shared.auth.app.boundary;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Boundary para persistência de sessões de refresh (public schema).
 *
 * Regra de arquitetura:
 * - shared.* depende apenas de shared.*
 * - infrastructure.* implementa este boundary via JPA
 */
public interface AuthRefreshSessionStore {

    /**
     * Insere uma nova sessão.
     */
    void insert(AuthRefreshSessionData data);

    /**
     * Busca sessão pelo hash do refresh token.
     */
    Optional<AuthRefreshSessionData> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * Atualiza sessão por rotação do refresh token.
     * (hash antigo -> hash novo)
     */
    void rotate(UUID sessionId,
                String newRefreshTokenHash,
                Instant now,
                UUID requestId,
                String ip,
                String userAgent);

    /**
     * Revoga uma sessão específica.
     */
    void revoke(UUID sessionId,
                Instant now,
                String revokedReasonJson);

    /**
     * Revoga todas as sessões do usuário no domínio + account.
     */
    int revokeAllForUser(String sessionDomain,
                         Long accountId,
                         Long userId,
                         Instant now,
                         String revokedReasonJson);
}
