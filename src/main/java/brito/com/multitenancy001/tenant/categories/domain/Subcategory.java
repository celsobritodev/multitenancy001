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
        name = "subcategories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subcategories_name_category",
                columnNames = {"category_id", "name"}
        )
)
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
public class Subcategory implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_subcategories_category")
    )
    private Category category;

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
    }

    public void restore() {
        if (!this.deleted) return;
        this.deleted = false;
        this.active = true;
    }
}

