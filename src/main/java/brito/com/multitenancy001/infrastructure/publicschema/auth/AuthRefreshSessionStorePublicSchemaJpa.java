package brito.com.multitenancy001.infrastructure.publicschema.auth;

import brito.com.multitenancy001.shared.auth.app.boundary.AuthRefreshSessionData;
import brito.com.multitenancy001.shared.auth.app.boundary.AuthRefreshSessionStore;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JPA do boundary AuthRefreshSessionStore (public schema).
 */
@Component
@RequiredArgsConstructor
public class AuthRefreshSessionStorePublicSchemaJpa implements AuthRefreshSessionStore {

    private final AuthRefreshSessionJpaRepository repo;
    private final PublicSchemaUnitOfWork uow;

    @Override
    public void insert(AuthRefreshSessionData data) {
        // Comentário: insere sessão no public schema
        uow.tx(() -> {
            AuthRefreshSessionEntity e = new AuthRefreshSessionEntity();
            e.setId(data.id());
            e.setSessionDomain(data.sessionDomain());
            e.setAccountId(data.accountId());
            e.setUserId(data.userId());
            e.setTenantSchema(data.tenantSchema());
            e.setRefreshTokenHash(data.refreshTokenHash());

            e.setCreatedAt(data.createdAt());
            e.setLastUsedAt(data.lastUsedAt());
            e.setRotatedAt(data.rotatedAt());
            e.setRevokedAt(data.revokedAt());

            e.setCreatedRequestId(data.createdRequestId());
            e.setLastRequestId(data.lastRequestId());

            e.setCreatedIp(data.createdIp());
            e.setLastIp(data.lastIp());

            e.setCreatedUserAgent(data.createdUserAgent());
            e.setLastUserAgent(data.lastUserAgent());

            e.setRevokedReasonJson(data.revokedReasonJson());

            repo.save(e);
            return null;
        });
    }

    @Override
    public Optional<AuthRefreshSessionData> findByRefreshTokenHash(String refreshTokenHash) {
        // Comentário: busca sessão no public schema
        return uow.readOnly(() ->
                repo.findByRefreshTokenHash(refreshTokenHash).map(AuthRefreshSessionStorePublicSchemaJpa::toData)
        );
    }

    @Override
    public void rotate(UUID sessionId,
                       String newRefreshTokenHash,
                       Instant now,
                       UUID requestId,
                       String ip,
                       String userAgent) {
        // Comentário: rotação do refresh hash + last_used
        uow.tx(() -> {
            AuthRefreshSessionEntity e = repo.findById(sessionId).orElse(null);
            if (e == null) return null;
            if (e.getRevokedAt() != null) return null;

            e.setRefreshTokenHash(newRefreshTokenHash);
            e.setRotatedAt(now);
            e.setLastUsedAt(now);

            e.setLastRequestId(requestId);
            e.setLastIp(ip);
            e.setLastUserAgent(userAgent);

            repo.save(e);
            return null;
        });
    }

    @Override
    public void revoke(UUID sessionId, Instant now, String revokedReasonJson) {
        // Comentário: revoga sessão (idempotente)
        uow.tx(() -> {
            repo.revokeIfNotRevoked(sessionId, now, revokedReasonJson);
            return null;
        });
    }

    @Override
    public int revokeAllForUser(String sessionDomain, Long accountId, Long userId, Instant now, String revokedReasonJson) {
        // Comentário: revoga todas as sessões do usuário naquele domínio
        return uow.tx(() -> repo.revokeAllForUser(sessionDomain, accountId, userId, now, revokedReasonJson));
    }

    private static AuthRefreshSessionData toData(AuthRefreshSessionEntity e) {
        return new AuthRefreshSessionData(
                e.getId(),
                e.getSessionDomain(),
                e.getAccountId(),
                e.getUserId(),
                e.getTenantSchema(),
                e.getRefreshTokenHash(),
                e.getCreatedAt(),
                e.getLastUsedAt(),
                e.getRotatedAt(),
                e.getRevokedAt(),
                e.getCreatedRequestId(),
                e.getLastRequestId(),
                e.getCreatedIp(),
                e.getLastIp(),
                e.getCreatedUserAgent(),
                e.getLastUserAgent(),
                e.getRevokedReasonJson()
        );
    }
}
