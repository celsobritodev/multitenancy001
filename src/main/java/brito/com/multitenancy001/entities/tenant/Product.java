package brito.com.multitenancy001.entities.tenant;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_name", columnList = "name"),
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_supplier", columnList = "supplier_id"),
    @Index(name = "idx_product_created_at", columnList = "created_at")
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
    private String id;
    
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
    
    
    
    
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
      foreignKey = @ForeignKey(name = "fk_products_subcategory_category"),
      value = {
        @JoinColumn(name = "subcategory_id", referencedColumnName = "id"),
        @JoinColumn(name = "category_id", referencedColumnName = "category_id")
      }
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
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        
        if (this.stockQuantity == null) {
            this.stockQuantity = 0;
        }
        
        if (this.active == null) {
            this.active = true;
        }
        
        // Calcula margem de lucro se preço de custo for informado
        calculateProfitMargin();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        calculateProfitMargin();
    }
    
    /**
     * Calcula a margem de lucro se o preço de custo estiver disponível
     */
    private void calculateProfitMargin() {
        if (this.costPrice != null && this.costPrice.compareTo(BigDecimal.ZERO) > 0 && this.price != null) {
            BigDecimal profit = this.price.subtract(this.costPrice);
            this.profitMargin = profit
                    .divide(this.costPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
    }
    
    /**
     * Verifica se o produto está disponível para venda
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(this.active) && 
               !Boolean.TRUE.equals(this.deleted) && 
               this.stockQuantity > 0;
    }
    
    /**
     * Verifica se o estoque está baixo
     */
    public boolean isLowStock() {
        return this.minStock != null && this.stockQuantity <= this.minStock;
    }
    
    /**
     * Verifica se o estoque está acima do máximo
     */
    public boolean isOverStock() {
        return this.maxStock != null && this.stockQuantity > this.maxStock;
    }
    
    /**
     * Calcula o valor total do estoque (preço * quantidade)
     */
    public BigDecimal getInventoryValue() {
        return this.price.multiply(BigDecimal.valueOf(this.stockQuantity));
    }
    
    /**
     * Adiciona quantidade ao estoque
     */
    public void addToStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        this.stockQuantity += quantity;
    }
    
    /**
     * Remove quantidade do estoque
     */
    public void removeFromStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        
        if (this.stockQuantity < quantity) {
            throw new IllegalStateException("Estoque insuficiente. Disponível: " + this.stockQuantity);
        }
        
        this.stockQuantity -= quantity;
    }
    
    /**
     * Soft delete do produto
     */
    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.active = false;
    }
    
    /**
     * Restaura produto deletado
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.active = true;
    }
    
    /**
     * Atualiza preço e recalcula margem
     */
    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço inválido");
        }
        this.price = newPrice;
        calculateProfitMargin();
    }
    
    /**
     * Atualiza preço de custo e recalcula margem
     */
    public void updateCostPrice(BigDecimal newCostPrice) {
        this.costPrice = newCostPrice;
        calculateProfitMargin();
    }
}