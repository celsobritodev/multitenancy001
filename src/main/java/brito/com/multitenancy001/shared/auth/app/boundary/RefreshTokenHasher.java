package brito.com.multitenancy001.shared.auth.app.boundary;

/**
 * Boundary para hashing de refresh token.
 *
 * Motivo:
 * - Nunca persistir token puro
 * - shared não deve depender de implementação concreta (infra)
 */
public interface RefreshTokenHasher {

    /**
     * Hash canônico do refresh token (ex.: SHA-256 hex).
     */
    String hash(String refreshToken);
}
