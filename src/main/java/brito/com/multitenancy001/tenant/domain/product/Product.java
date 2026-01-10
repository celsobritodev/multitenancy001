package brito.com.multitenancy001.tenant.domain.product;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.tenant.domain.category.Category;
import brito.com.multitenancy001.tenant.domain.category.Subcategory;
import brito.com.multitenancy001.tenant.domain.supplier.Supplier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_name", columnList = "name"),
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_supplier", columnList = "supplier_id"),
    @Index(name = "idx_product_created_at", columnList = "created_at"),
    @Index(name = "idx_products_category_id", columnList = "category_id"),
    @Index(name = "idx_products_subcategory_id", columnList = "subcategory_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"supplier", "category", "subcategory"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, length = 100)
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

    // ✅ FK simples (category_id) - obrigatório
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "category_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_products_category")
    )
    private Category category;

    // ✅ CORREÇÃO: FK simples para subcategory (REMOVER a composta)
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

    @Column(name = "active")
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

    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = false;

    @PrePersist
    protected void onCreate() {
       
        if (this.stockQuantity == null) this.stockQuantity = 0;
        if (this.active == null) this.active = true;
        calculateProfitMargin();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateProfitMargin();
    }

    private void calculateProfitMargin() {
        if (this.costPrice != null && this.costPrice.compareTo(BigDecimal.ZERO) > 0 && this.price != null) {
            BigDecimal profit = this.price.subtract(this.costPrice);
            this.profitMargin = profit
                .divide(this.costPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.active = false;
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