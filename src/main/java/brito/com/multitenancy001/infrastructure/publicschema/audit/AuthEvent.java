package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthDomainConverter;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
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
@Table(name = "auth_events")
public class AuthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Convert(converter = AuthDomainConverter.class)
    @Column(name = "auth_domain", length = 30)
    private AuthDomain authDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuthEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "principal_email")
    private String principalEmail;

    @Column(name = "principal_user_id")
    private Long principalUserId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "tenant_schema")
    private String tenantSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String detailsJson;
}
