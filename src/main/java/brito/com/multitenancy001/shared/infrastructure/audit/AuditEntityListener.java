package brito.com.multitenancy001.shared.infrastructure.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuditEntityListener {

    private AuditActorProvider auditActorProvider;

    // ✅ robusto: Hibernate pode instanciar por reflexão
    public AuditEntityListener() {
    }

    @Autowired
    public void setProvider(AuditActorProvider auditActorProvider) {
        this.auditActorProvider = auditActorProvider;
    }

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;
        auditable.getAudit().onCreate(currentActorOrThrow());
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (!(entity instanceof Auditable auditable)) return;

        var actor = currentActorOrThrow();
        auditable.getAudit().onUpdate(actor);

        if (entity instanceof SoftDeletable softDeletable && softDeletable.isDeleted() && auditable.getAudit().getDeletedBy() == null) {
            auditable.getAudit().onDelete(actor);
        }
    }

    private AuditActor currentActorOrThrow() {
        if (auditActorProvider == null) {
            throw new IllegalStateException(
                    "AuditActorProvider não foi injetado no AuditEntityListener. " +
                    "Verifique BEAN_CONTAINER no EMF (PUBLIC e TENANT) e se AuditActorProvider é bean Spring."
            );
        }
        return auditActorProvider.current();
    }
}
