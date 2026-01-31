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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Column(name = "profit_margin", precision = 5, scale = 2)
    private BigDecimal profitMargin;

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

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "weight_kg", precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "dimensions", length = 50)
    private String dimensions;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "images_json", columnDefinition = "TEXT")
    private String imagesJson;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", foreignKey = @ForeignKey(name = "fk_product_supplier"))
    private Supplier supplier;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        if (this.stockQuantity == null) this.stockQuantity = 0;
        if (this.active == null) this.active = true;
        if (this.deleted == null) this.deleted = false;
        calculateProfitMargin();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateProfitMargin();
    }
    
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }


    private void calculateProfitMargin() {
        if (this.costPrice != null && this.costPrice.compareTo(BigDecimal.ZERO) > 0 && this.price != null) {
            BigDecimal profit = this.price.subtract(this.costPrice);

            // % = (profit / cost) * 100
            BigDecimal percent = profit
                    .divide(this.costPrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // ✅ alinha com NUMERIC(5,2)
            this.profitMargin = percent.setScale(2, RoundingMode.HALF_UP);
        } else {
            this.profitMargin = null;
        }
    }

    // =====================
    // Regras de domínio
    // =====================

    public void softDelete(LocalDateTime now) {
        if (Boolean.TRUE.equals(this.deleted)) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.deletedAt = now;
        this.active = false;
    }

    public void restore() {
        if (!Boolean.TRUE.equals(this.deleted)) return;

        this.deleted = false;
        this.deletedAt = null;
        this.active = true;
    }

    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço inválido");
        }
        this.price = newPrice;
        calculateProfitMargin();
    }

    public void updateCostPrice(BigDecimal newCostPrice) {
        this.costPrice = newCostPrice;
        calculateProfitMargin();
    }

    public void addToStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        if (this.stockQuantity == null) this.stockQuantity = 0;
        this.stockQuantity += quantity;
    }

    public void removeFromStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        if (this.stockQuantity == null) this.stockQuantity = 0;

        if (this.stockQuantity < quantity) {
            throw new IllegalStateException("Estoque insuficiente. Disponível: " + this.stockQuantity);
        }
        this.stockQuantity -= quantity;
    }
}
