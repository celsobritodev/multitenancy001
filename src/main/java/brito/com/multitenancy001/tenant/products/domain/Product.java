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

/**
 * Aggregate Root (Tenant): Product.
 *
 * Regra unica de margem (definitiva):
 * - profitMargin = "MARGEM %" (margin), nao "markup".
 * - Formula: margin% = ((price - costPrice) / price) * 100
 *
 * Persistencia / consistencia:
 * - Para garantir que o retorno da API sempre traga profitMargin atualizado,
 *   esta entidade recalcula automaticamente a margem em @PrePersist/@PreUpdate.
 */
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
     * Percentual de margem (0..100..), scale(2).
     * Ex.: 25.00 significa 25% de margem sobre o preco.
     *
     * DDL: NUMERIC(12,2).
     */
    @Column(name = "profit_margin", precision = 12, scale = 2)
    private BigDecimal profitMargin;

    @Column(length = 100)
    private String brand;

    // SQL: weight_kg NUMERIC(8,3)
    @Column(name = "weight_kg", precision = 8, scale = 3)
    private BigDecimal weightKg;

    // SQL: dimensions VARCHAR(50)
    @Column(length = 50)
    private String dimensions;

    // SQL: barcode VARCHAR(50)
    @Column(length = 50)
    private String barcode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // =========================
    // SOFT DELETE + AUDIT UNICO
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
            foreignKey = @ForeignKey(name = "fk_product_supplier") // <-- SQL
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
    // JPA LIFECYCLE (garantia)
    // =========================

    @PrePersist
    @PreUpdate
    private void onPersistOrUpdate() {
        recomputeProfitMargin();
    }

    // =========================
    // DOMAIN METHODS (estoque)
    // =========================

    public void addToStock(Integer qty) {
        if (qty == null || qty <= 0) return;
        if (this.stockQuantity == null) this.stockQuantity = 0;
        this.stockQuantity = this.stockQuantity + qty;
    }

    public void removeFromStock(int qty) {
        if (qty <= 0) return;
        if (this.stockQuantity == null) this.stockQuantity = 0;

        int next = this.stockQuantity - qty;
        if (next < 0) throw new IllegalStateException("Stock cannot be negative");
        this.stockQuantity = next;
    }

    // =========================
    // DOMAIN METHODS (preco/custo/margem)
    // =========================

    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null) throw new IllegalArgumentException("newPrice is required");
        if (newPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("newPrice cannot be negative");

        this.price = newPrice;
        recomputeProfitMargin();
    }

    public void updateCostPrice(BigDecimal newCostPrice) {
        if (newCostPrice == null) throw new IllegalArgumentException("newCostPrice is required");
        if (newCostPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("newCostPrice cannot be negative");

        this.costPrice = newCostPrice;
        recomputeProfitMargin();
    }

    /**
     * Regra unica (MARGEM %):
     * margin% = ((price - costPrice) / price) * 100
     *
     * - Se price == null ou price <= 0 => profitMargin = null
     * - Se costPrice == null => profitMargin = null
     * - Se costPrice > price => margem negativa (aceito)
     */
    public void recomputeProfitMargin() {
        if (this.price == null || this.price.compareTo(BigDecimal.ZERO) <= 0) {
            this.profitMargin = null;
            return;
        }
        if (this.costPrice == null) {
            this.profitMargin = null;
            return;
        }

        BigDecimal profit = this.price.subtract(this.costPrice);

        BigDecimal margin = profit
                .divide(this.price, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        this.profitMargin = margin;
    }

    // =========================
    // DOMAIN METHODS (soft delete / restore)
    // =========================

    public void softDelete() {
        if (Boolean.TRUE.equals(this.deleted)) return;
        this.deleted = true;
        this.active = false;
    }

    public void softDelete(Instant now) {
        if (Boolean.TRUE.equals(this.deleted)) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.active = false;

        if (this.audit != null) this.audit.markDeleted(now);
    }

    public void restore() {
        if (!Boolean.TRUE.equals(this.deleted)) return;

        this.deleted = false;
        this.active = true;

        if (this.audit != null) this.audit.clearDeleted();
    }
}