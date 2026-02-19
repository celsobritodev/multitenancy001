package brito.com.multitenancy001.infrastructure.publicschema.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA (public schema) para sessões de refresh.
 *
 * IMPORTANTE:
 * - Mantém dados mínimos para rotação/revogação
 * - Não armazena token puro (somente hash)
 */
@Entity
@Table(name = "auth_refresh_sessions")
@Getter
@Setter
public class AuthRefreshSessionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_domain", nullable = false, length = 32)
    private String sessionDomain;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_schema", length = 128)
    private String tenantSchema;

    @Column(name = "refresh_token_hash", nullable = false, length = 128, unique = true)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_request_id")
    private UUID createdRequestId;

    @Column(name = "last_request_id")
    private UUID lastRequestId;

    @Column(name = "created_ip", length = 64)
    private String createdIp;

    @Column(name = "last_ip", length = 64)
    private String lastIp;

    @Column(name = "created_user_agent", length = 512)
    private String createdUserAgent;

    @Column(name = "last_user_agent", length = 512)
    private String lastUserAgent;

    @Column(name = "revoked_reason_json", columnDefinition = "text")
    private String revokedReasonJson;
}
