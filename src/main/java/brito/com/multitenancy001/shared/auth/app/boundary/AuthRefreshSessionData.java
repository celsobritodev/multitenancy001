package brito.com.multitenancy001.shared.auth.app.boundary;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de persistência (boundary) para sessão de refresh.
 */
public record AuthRefreshSessionData(
        UUID id,
        String sessionDomain,       // "CONTROLPLANE" | "TENANT"
        Long accountId,
        Long userId,
        String tenantSchema,        // null p/ CP
        String refreshTokenHash,

        Instant createdAt,
        Instant lastUsedAt,
        Instant rotatedAt,
        Instant revokedAt,

        UUID createdRequestId,
        UUID lastRequestId,

        String createdIp,
        String lastIp,

        String createdUserAgent,
        String lastUserAgent,

        String revokedReasonJson
) {}
