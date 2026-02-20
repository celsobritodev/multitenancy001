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
 * Regra única de margem (definitiva):
 * - profitMargin = "MARGEM %" (margin), não "markup".
 * - Fórmula: margin% = ((price - costPrice) / price) * 100
 *
 * Persistência / consistência:
 * - Para garantir que o retorno da API sempre traga profitMargin atualizado,
 *   esta entidade recalcula automaticamente a margem em @PrePersist/@PreUpdate
 *   (mesmo se alguém usar setters direto).
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
     * Ex.: 25.00 significa 25% de margem sobre o preço.
     *
     * DDL atual: NUMERIC(12,2) está ótimo.
     */
    @Column(name = "profit_margin", precision = 12, scale = 2)
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
    // JPA LIFECYCLE (garantia)
    // =========================

    @PrePersist
    @PreUpdate
    private void onPersistOrUpdate() {
        // método: garante consistência do campo derivado
        recomputeProfitMargin();
    }

    // =========================
    // DOMAIN METHODS (estoque)
    // =========================

    public void addToStock(Integer qty) {
        // método: adiciona ao estoque de forma segura
        if (qty == null || qty <= 0) return;
        if (this.stockQuantity == null) this.stockQuantity = 0;
        this.stockQuantity = this.stockQuantity + qty;
    }

    public void removeFromStock(int qty) {
        // método: remove do estoque garantindo não-negativo
        if (qty <= 0) return;
        if (this.stockQuantity == null) this.stockQuantity = 0;

        int next = this.stockQuantity - qty;
        if (next < 0) throw new IllegalStateException("Stock cannot be negative");
        this.stockQuantity = next;
    }

    // =========================
    // DOMAIN METHODS (preço/custo/margem)
    // =========================

    public void updatePrice(BigDecimal newPrice) {
        // método: valida, aplica preço e recalcula margem
        if (newPrice == null) throw new IllegalArgumentException("newPrice is required");
        if (newPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("newPrice cannot be negative");

        this.price = newPrice;
        recomputeProfitMargin();
    }

    public void updateCostPrice(BigDecimal newCostPrice) {
        // método: valida, aplica custo e recalcula margem
        if (newCostPrice == null) throw new IllegalArgumentException("newCostPrice is required");
        if (newCostPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("newCostPrice cannot be negative");

        this.costPrice = newCostPrice;
        recomputeProfitMargin();
    }

    /**
     * Regra única (MARGEM %):
     * margin% = ((price - costPrice) / price) * 100
     *
     * - Se price == null ou price <= 0 => profitMargin = null
     * - Se costPrice == null => profitMargin = null
     * - Se costPrice > price => margem negativa (aceito para sinalizar prejuízo)
     */
    public void recomputeProfitMargin() {
        // método: recalcula profitMargin de forma determinística
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
                .divide(this.price, 10, RoundingMode.HALF_UP) // precisão interna
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        this.profitMargin = margin;
    }

    // =========================
    // DOMAIN METHODS (soft delete / restore)
    // =========================

    public void softDelete() {
        // método: marca como deletado (idempotente)
        if (Boolean.TRUE.equals(this.deleted)) return;
        this.deleted = true;
        this.active = false;
    }

    public void softDelete(Instant now) {
        // método: marca como deletado e registra deletedAt (quando possível)
        if (Boolean.TRUE.equals(this.deleted)) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.active = false;

        if (this.audit != null) this.audit.markDeleted(now);
    }

    public void restore() {
        // método: restaura soft-delete (idempotente)
        if (!Boolean.TRUE.equals(this.deleted)) return;

        this.deleted = false;
        this.active = true;

        if (this.audit != null) this.audit.clearDeleted();
    }
}