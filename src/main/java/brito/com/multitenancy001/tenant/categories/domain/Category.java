package brito.com.multitenancy001.tenant.categories.domain;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categories_name",
                columnNames = "name"
        )
)
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
public class Category implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @Embedded
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    public void softDelete() {
        if (this.deleted) return;
        this.deleted = true;
        this.active = false;
        // deletedAt será setado pelo AuditEntityListener (audit.deletedAt)
    }

    public void restore() {
        if (!this.deleted) return;
        this.deleted = false;
        this.active = true;
        // deletedAt será limpo pelo AuditEntityListener se você quiser (opcional).
        // Se sua política for manter histórico, NÃO limpe. Se for "restore limpa", ajuste listener.
    }
}

