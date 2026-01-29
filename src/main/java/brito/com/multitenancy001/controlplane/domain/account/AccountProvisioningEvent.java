package brito.com.multitenancy001.controlplane.domain.account;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "account_provisioning_events", schema = "public")
public class AccountProvisioningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AccountProvisioningEvent() { }

    public AccountProvisioningEvent(
            Long accountId,
            String eventType,
            ProvisioningFailureCode failureCode,
            String message,
            String detailsJson
    ) {
        this.accountId = accountId;
        this.eventType = eventType;
        this.failureCode = (failureCode == null ? null : failureCode.name());
        this.message = message;
        this.detailsJson = detailsJson;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
}
