// src/main/java/brito/com/multitenancy001/shared/auth/app/boundary/AuthRefreshSessionData.java
package brito.com.multitenancy001.shared.auth.app.boundary;

import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de persistência (boundary) para sessão de refresh.
 *
 * Contrato:
 * - sessionDomain é tipado (evita "TENANT"/"CONTROLPLANE" solto).
 * - tenantSchema é obrigatório apenas para TENANT e deve ser null para CONTROLPLANE.
 */
public record AuthRefreshSessionData(
        UUID id,
        AuthSessionDomain sessionDomain,
        Long accountId,
        Long userId,
        String tenantSchema,
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