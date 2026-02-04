package brito.com.multitenancy001.shared.domain.audit.jpa;

import java.time.Instant;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;

public class AuditEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        // FAIL-FAST se wiring quebrou (provider ausente)
        AuditActor actor = AuditActorProviders.currentOrFail();
        Instant now = AuditClockProviders.nowOrFail();

        auditable.getAudit().onCreate(actor, now);

        if (entity instanceof SoftDeletable softDeletable
                && softDeletable.isDeleted()
                && auditable.getAudit().getDeletedAt() == null) {
            auditable.getAudit().onDelete(actor, now);
        }
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        AuditActor actor = AuditActorProviders.currentOrFail();
        Instant now = AuditClockProviders.nowOrFail();

        auditable.getAudit().onUpdate(actor, now);

        if (entity instanceof SoftDeletable softDeletable
                && softDeletable.isDeleted()
                && auditable.getAudit().getDeletedAt() == null) {
            auditable.getAudit().onDelete(actor, now);
        }
    }
}
