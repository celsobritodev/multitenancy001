package brito.com.multitenancy001.shared.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter
@Setter
public class AuditInfo {

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private Instant deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "updated_by_email")
    private String updatedByEmail;

    @Column(name = "deleted_by_email")
    private String deletedByEmail;

    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    public void clearDeleted() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletedByEmail = null;
    }
}

