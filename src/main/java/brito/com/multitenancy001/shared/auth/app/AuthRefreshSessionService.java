package brito.com.multitenancy001.shared.auth.app;

import brito.com.multitenancy001.shared.auth.app.boundary.AuthRefreshSessionData;
import brito.com.multitenancy001.shared.auth.app.boundary.AuthRefreshSessionStore;
import brito.com.multitenancy001.shared.auth.app.boundary.RefreshTokenHasher;
import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service de sessões de refresh (logout forte + rotação).
 *
 * Regras:
 * - Emitir sessão ao fazer login (refresh gerado)
 * - Rotacionar sessão a cada refresh (troca do hash)
 * - Revogar sessão ao logout forte
 */
@Service
@RequiredArgsConstructor
public class AuthRefreshSessionService {

    private final AuthRefreshSessionStore store;
    private final RefreshTokenHasher hasher;
    private final AppClock appClock;

    /**
     * Registra uma sessão nova no servidor quando um refresh token é emitido.
     */
    public void onRefreshIssued(String sessionDomain,
                               Long accountId,
                               Long userId,
                               String tenantSchemaOrNull,
                               String refreshToken) {
        // Comentário: validação de entrada e persistência de sessão
        if (!StringUtils.hasText(sessionDomain)) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "sessionDomain ausente", 500);
        }
        if (accountId == null || userId == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "accountId/userId ausente", 500);
        }
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken ausente", 400);
        }

        Instant now = appClock.instant();
        RequestMeta meta = RequestMetaContext.getOrNull();

        UUID requestId = meta != null ? meta.requestId() : null;
        String ip = meta != null ? meta.ip() : null;
        String userAgent = meta != null ? meta.userAgent() : null;

        AuthRefreshSessionData data = new AuthRefreshSessionData(
                UUID.randomUUID(),
                sessionDomain.trim().toUpperCase(),
                accountId,
                userId,
                (tenantSchemaOrNull != null ? tenantSchemaOrNull : null),
                hasher.hash(refreshToken),
                now,
                now,
                null,
                null,
                requestId,
                requestId,
                ip,
                ip,
                userAgent,
                userAgent,
                null
        );

        store.insert(data);
    }

    /**
     * Rotaciona o refresh token (old -> new) validando que o old existe e não está revogado.
     */
    public void rotateOrThrow(String sessionDomain,
                              String oldRefreshToken,
                              String newRefreshToken,
                              Long expectedAccountId,
                              Long expectedUserId,
                              String expectedTenantSchemaOrNull) {
        // Comentário: valida old, busca sessão, valida ownership e rotaciona hash
        if (!StringUtils.hasText(oldRefreshToken) || !StringUtils.hasText(newRefreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        String oldHash = hasher.hash(oldRefreshToken);

        AuthRefreshSessionData s = store.findByRefreshTokenHash(oldHash)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken revogado/expirado", 401));

        if (s.revokedAt() != null) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken revogado", 401);
        }

        String dom = (sessionDomain != null ? sessionDomain.trim().toUpperCase() : null);
        if (dom == null || !dom.equals(s.sessionDomain())) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (domínio)", 401);
        }
        if (expectedAccountId != null && !expectedAccountId.equals(s.accountId())) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (account)", 401);
        }
        if (expectedUserId != null && !expectedUserId.equals(s.userId())) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (user)", 401);
        }

        String expectedTenant = expectedTenantSchemaOrNull != null ? expectedTenantSchemaOrNull.trim() : null;
        String actualTenant = s.tenantSchema() != null ? s.tenantSchema().trim() : null;

        if (expectedTenant != null && (actualTenant == null || !expectedTenant.equals(actualTenant))) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (tenantSchema)", 401);
        }

        Instant now = appClock.instant();
        RequestMeta meta = RequestMetaContext.getOrNull();

        UUID requestId = meta != null ? meta.requestId() : null;
        String ip = meta != null ? meta.ip() : null;
        String userAgent = meta != null ? meta.userAgent() : null;

        store.rotate(
                s.id(),
                hasher.hash(newRefreshToken),
                now,
                requestId,
                ip,
                userAgent
        );
    }

    /**
     * Logout forte: revoga uma sessão pelo refresh token.
     */
    public void revokeByRefreshTokenOrThrow(String refreshToken, String revokedReasonJson) {
        // Comentário: resolve sessão pelo hash e revoga
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        String hash = hasher.hash(refreshToken);

        AuthRefreshSessionData s = store.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401));

        if (s.revokedAt() != null) {
            return; // idempotente
        }

        store.revoke(s.id(), appClock.instant(), revokedReasonJson);
    }

    /**
     * Logout forte (all devices): revoga todas as sessões do usuário naquele domínio.
     */
    public int revokeAllForUser(String sessionDomain,
                                Long accountId,
                                Long userId,
                                String revokedReasonJson) {
        // Comentário: revoga em massa por domínio + user
        if (!StringUtils.hasText(sessionDomain) || accountId == null || userId == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Parâmetros inválidos para revokeAll", 500);
        }
        return store.revokeAllForUser(sessionDomain.trim().toUpperCase(), accountId, userId, appClock.instant(), revokedReasonJson);
    }
}
