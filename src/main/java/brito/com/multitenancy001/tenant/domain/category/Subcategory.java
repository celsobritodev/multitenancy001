package brito.com.multitenancy001.tenant.domain.category;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.infrastructure.audit.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Embedded
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() { return audit; }

    @Override
    public boolean isDeleted() { return deleted; }

    public void softDelete(LocalDateTime now) {
        if (this.deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.deletedAt = now;
        this.active = false;
    }

    public void restore() {
        if (!this.deleted) return;

        this.deleted = false;
        this.deletedAt = null;
        this.active = true;
    }
}
