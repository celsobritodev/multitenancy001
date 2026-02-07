package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Padronizado: Instant -> timestamptz
    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamptz")
    private Instant occurredAt;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "method")
    private String method;

    @Column(name = "uri")
    private String uri;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private SecurityAuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private AuditOutcome outcome;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_email")
    private String targetEmail;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "tenant_schema")
    private String tenantSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String detailsJson;
}
