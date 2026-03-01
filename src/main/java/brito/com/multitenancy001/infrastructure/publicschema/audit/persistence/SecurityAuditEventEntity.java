package brito.com.multitenancy001.infrastructure.publicschema.audit.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Entidade de audit no schema PUBLIC.
 *
 * Observação: Se você já tem tabela/entidade, adapte os campos.
 */
@Getter
@Setter
@Entity
@Table(name = "security_audit_event", schema = "public")
public class SecurityAuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "tenant_schema")
    private String tenantSchema;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "details_json", nullable = false, columnDefinition = "TEXT")
    private String detailsJson;
}