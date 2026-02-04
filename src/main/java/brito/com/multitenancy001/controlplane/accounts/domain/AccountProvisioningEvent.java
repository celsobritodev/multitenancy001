package brito.com.multitenancy001.controlplane.accounts.domain;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "account_provisioning_events", schema = "public")
public class AccountProvisioningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ProvisioningStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected AccountProvisioningEvent() { }

    public AccountProvisioningEvent(
            Long accountId,
            ProvisioningStatus status,
            ProvisioningFailureCode failureCode,
            String message,
            String detailsJson,
            Instant createdAt
    ) {
        this.accountId = accountId;
        this.status = status;
        this.failureCode = (failureCode == null ? null : failureCode.name());
        this.message = message;
        this.detailsJson = detailsJson;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public ProvisioningStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getMessage() { return message; }
    public String getDetailsJson() { return detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}

