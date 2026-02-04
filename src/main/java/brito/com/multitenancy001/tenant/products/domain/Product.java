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
import java.time.Instant;
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
    @JoinColumn(
            name = "supplier_id",
            foreignKey = @ForeignKey(name = "fk_products_supplier")
    )
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_category")
    )
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "subcategory_id",
            foreignKey = @ForeignKey(name = "fk_products_subcategory")
    )
    private Subcategory subcategory;

    // =========================
    // CONTRACTS
    // =========================

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    // =========================
    // DOMAIN METHODS (estoque/preço/delete)
    // =========================

    public void addToStock(Integer qty) {
        if (qty == null) return;
        if (qty <= 0) return;

        if (this.stockQuantity == null) this.stockQuantity = 0;
        this.stockQuantity = this.stockQuantity + qty;
    }

    public void removeFromStock(int qty) {
        if (qty <= 0) return;

        if (this.stockQuantity == null) this.stockQuantity = 0;

        int next = this.stockQuantity - qty;
        if (next < 0) {
            throw new IllegalStateException("Stock cannot be negative");
        }
        this.stockQuantity = next;
    }

    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null) throw new IllegalArgumentException("newPrice is required");
        if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("newPrice cannot be negative");
        }
        this.price = newPrice;
        recomputeProfitMargin();
    }

    public void updateCostPrice(BigDecimal newCostPrice) {
        // custo pode ser null (sem custo informado)
        if (newCostPrice != null && newCostPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("costPrice cannot be negative");
        }
        this.costPrice = newCostPrice;
        recomputeProfitMargin();
    }

    /**
     * Compat: chamado por código que usa softDelete() sem now.
     * Auditoria de deletedAt/deletedBy fica com o AuditEntityListener.
     */
    public void softDelete() {
        if (Boolean.TRUE.equals(this.deleted)) return;
        this.deleted = true;
        this.active = false;
    }

    /**
     * ✅ Novo: compat com seu service atual: product.softDelete(appClock.instant()).
     * - Mantém deleted=true e active=false
     * - Se quiser registrar deletedAt imediatamente, usa audit.markDeleted(now)
     *   (seu AuditInfo já tem markDeleted(Instant)).
     */
    public void softDelete(Instant now) {
        if (Boolean.TRUE.equals(this.deleted)) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.active = false;

        if (this.audit != null) {
            this.audit.markDeleted(now);
        }
    }

    public void restore() {
        if (!Boolean.TRUE.equals(this.deleted)) return;
        this.deleted = false;
        this.active = true;

        if (this.audit != null) {
            this.audit.clearDeleted();
        }
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
