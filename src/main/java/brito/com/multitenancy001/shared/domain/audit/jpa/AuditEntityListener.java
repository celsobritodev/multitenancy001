package brito.com.multitenancy001.shared.domain.audit.jpa;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Listener de auditoria (JPA/Hibernate).
 *
 * Importante:
 * - Este listener é instanciado pelo JPA, NÃO pelo Spring.
 * - Por isso, NÃO use @Component/@Autowired aqui.
 * - A ponte com Spring é feita via AuditActorProviders (holder estático).
 */
public class AuditEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        AuditActor actor = AuditActorProviders.currentOrSystem();
        auditable.getAudit().onCreate(actor);

        if (entity instanceof SoftDeletable softDeletable
                && softDeletable.isDeleted()
                && auditable.getAudit().getDeletedBy() == null) {
            auditable.getAudit().onDelete(actor);
        }
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        AuditActor actor = AuditActorProviders.currentOrSystem();
        auditable.getAudit().onUpdate(actor);

        if (entity instanceof SoftDeletable softDeletable
                && softDeletable.isDeleted()
                && auditable.getAudit().getDeletedBy() == null) {
            auditable.getAudit().onDelete(actor);
        }
    }
}
