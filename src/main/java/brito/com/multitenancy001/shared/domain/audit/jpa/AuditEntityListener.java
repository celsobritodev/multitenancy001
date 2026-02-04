package brito.com.multitenancy001.shared.domain.audit.jpa;

import java.time.Instant;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;

/**
 * Listener de auditoria (JPA/Hibernate).
 *
 * Regras:
 * - NÃO use @Component/@Autowired aqui.
 * - Tempo SEMPRE via AppClock, acessado por AuditClockProviders.
 * - Ator via AuditActorProviders.
 */
public class AuditEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        AuditActor actor = AuditActorProviders.currentOrSystem();
        Instant now = AuditClockProviders.nowOrSystem();

        auditable.getAudit().onCreate(actor, now);

        // Se já vier "deleted=true" no INSERT, grava deleção também
        if (entity instanceof SoftDeletable softDeletable
                && softDeletable.isDeleted()
                && auditable.getAudit().getDeletedAt() == null) {
            auditable.getAudit().onDelete(actor, now);
        }
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        AuditActor actor = AuditActorProviders.currentOrSystem();
        Instant now = AuditClockProviders.nowOrSystem();

        auditable.getAudit().onUpdate(actor, now);

        if (entity instanceof SoftDeletable softDeletable
                && softDeletable.isDeleted()
                && auditable.getAudit().getDeletedAt() == null) {
            auditable.getAudit().onDelete(actor, now);
        }
    }
}

