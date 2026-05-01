package brito.com.multitenancy001.shared.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.boundary.AuthRefreshSessionData;
import brito.com.multitenancy001.shared.auth.app.boundary.AuthRefreshSessionStore;
import brito.com.multitenancy001.shared.auth.app.boundary.RefreshTokenHasher;
import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;
import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service de sessões de refresh (logout forte + rotação).
 */
@Service
@RequiredArgsConstructor
public class AuthRefreshSessionService {

    private final AuthRefreshSessionStore store;
    private final RefreshTokenHasher hasher;
    private final AppClock appClock;

    public void onRefreshIssued(AuthSessionDomain sessionDomain,
                               Long accountId,
                               Long userId,
                               String tenantSchemaOrNull,
                               String refreshToken) {

        if (sessionDomain == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "sessionDomain ausente");
        }

        if (accountId == null || userId == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "accountId/userId ausente");
        }

        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken ausente");
        }

        String tenantSchema = normalizeTenantSchema(sessionDomain, tenantSchemaOrNull);

        Instant now = appClock.instant();
        RequestMeta meta = RequestMetaContext.getOrNull();

        UUID requestId = meta != null ? meta.requestId() : null;
        String ip = meta != null ? meta.ip() : null;
        String userAgent = meta != null ? meta.userAgent() : null;

        AuthRefreshSessionData data = new AuthRefreshSessionData(
                UUID.randomUUID(),
                sessionDomain,
                accountId,
                userId,
                tenantSchema,
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

    public void rotateOrThrow(AuthSessionDomain sessionDomain,
                              String oldRefreshToken,
                              String newRefreshToken,
                              Long expectedAccountId,
                              Long expectedUserId,
                              String expectedTenantSchemaOrNull) {

        if (sessionDomain == null) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (domínio)");
        }

        if (!StringUtils.hasText(oldRefreshToken) || !StringUtils.hasText(newRefreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido");
        }

        String oldHash = hasher.hash(oldRefreshToken);

        AuthRefreshSessionData s = store.findByRefreshTokenHash(oldHash)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken revogado/expirado"));

        if (s.revokedAt() != null) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken revogado");
        }

        if (s.sessionDomain() != sessionDomain) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (domínio)");
        }

        if (expectedAccountId != null && !expectedAccountId.equals(s.accountId())) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (account)");
        }

        if (expectedUserId != null && !expectedUserId.equals(s.userId())) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (user)");
        }

        String expectedTenant = normalizeTenantSchema(sessionDomain, expectedTenantSchemaOrNull);
        String actualTenant = s.tenantSchema() != null ? s.tenantSchema().trim() : null;

        if (sessionDomain == AuthSessionDomain.TENANT && !expectedTenant.equals(actualTenant)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (tenantSchema)");
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

    public void revokeByRefreshTokenOrThrow(String refreshToken, String revokedReasonJson) {

        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório");
        }

        String hash = hasher.hash(refreshToken);

        AuthRefreshSessionData s = store.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido"));

        if (s.revokedAt() != null) {
            return;
        }

        store.revoke(s.id(), appClock.instant(), revokedReasonJson);
    }

    public int revokeAllForUser(AuthSessionDomain sessionDomain,
                                Long accountId,
                                Long userId,
                                String revokedReasonJson) {

        if (sessionDomain == null || accountId == null || userId == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Parâmetros inválidos para revokeAll");
        }

        return store.revokeAllForUser(sessionDomain, accountId, userId, appClock.instant(), revokedReasonJson);
    }

    private static String normalizeTenantSchema(AuthSessionDomain sessionDomain, String tenantSchemaOrNull) {

        if (sessionDomain == AuthSessionDomain.CONTROLPLANE) {
            return null;
        }

        String schema = tenantSchemaOrNull != null ? tenantSchemaOrNull.trim() : null;

        if (!StringUtils.hasText(schema)) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "tenantSchema ausente para sessão TENANT");
        }

        return schema;
    }
}