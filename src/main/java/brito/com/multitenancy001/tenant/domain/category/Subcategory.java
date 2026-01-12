package brito.com.multitenancy001.tenant.domain.category;

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
@Getter
@Setter
public class Subcategory {

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

    // Negócio
    @Column(nullable = false)
    private boolean active = true;

    // Soft delete
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Auditoria
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================
    // Regras de domínio
    // =====================

    public void softDelete() {
        if (this.deleted) return;
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.active = false;
    }

    public void restore() {
        if (!this.deleted) return;
        this.deleted = false;
        this.deletedAt = null;
        this.active = true;
    }
}
