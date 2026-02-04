package brito.com.multitenancy001.tenant.products.domain;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "products")
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"supplier", "category", "subcategory"})
public class Product implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100, nullable = false)
    private String sku;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "max_stock")
    private Integer maxStock;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    /**
     * Percentual (0..100) armazenado, calculado por regra.
     * Ex.: 25.00 significa 25%
     */
    @Column(name = "profit_margin", precision = 10, scale = 2)
    private BigDecimal profitMargin;

    @Column(length = 100)
    private String brand;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(length = 100)
    private String dimensions;

    @Column(length = 100)
    private String barcode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // =========================
    // SOFT DELETE + AUDIT ÚNICO
    // =========================

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // =========================
    // RELACIONAMENTOS
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id",
            foreignKey = @ForeignKey(name = "fk_products_supplier"))
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_category"))
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id",
            foreignKey = @ForeignKey(name = "fk_products_subcategory"))
    private Subcategory subcategory;

    // =========================
    // DOMAIN METHODS
    // =========================

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    public void softDelete() {
        if (Boolean.TRUE.equals(this.deleted)) return;
        this.deleted = true;
        this.active = false;
        // deletedAt/deletedBy serão preenchidos pelo AuditEntityListener (fonte única).
    }

    public void restore() {
        if (!Boolean.TRUE.equals(this.deleted)) return;
        this.deleted = false;
        this.active = true;
        // Política de restore: manter deletedAt (histórico) ou limpar?
        // Seu listener hoje NÃO limpa. Se quiser "restore limpa", ajuste AuditInfo/AuditEntityListener.
    }

    /**
     * Regra de margem:
     * - Se costPrice e price existirem, calcula a margem (0..100).
     * - Se não, deixa null.
     */
    public void recomputeProfitMargin() {
        if (costPrice == null || price == null) {
            this.profitMargin = null;
            return;
        }
        if (costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            this.profitMargin = null;
            return;
        }
        BigDecimal diff = price.subtract(costPrice);
        BigDecimal pct = diff
                .divide(costPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        this.profitMargin = pct;
    }
}

