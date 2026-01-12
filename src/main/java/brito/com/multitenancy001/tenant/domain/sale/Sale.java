package brito.com.multitenancy001.tenant.domain.sale;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_sale_date", columnList = "sale_date"),
        @Index(name = "idx_sales_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "items")
public class Sale {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Snapshot do cliente (se você não tem uma entidade Customer)
    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_document", length = 20)
    private String customerDocument;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status;

    @OneToMany(
            mappedBy = "sale",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========= helpers =========

    public void addItem(SaleItem item) {
        if (item == null) return;
        item.setSale(this);
        this.items.add(item);
        recalcTotal();
    }

    public void removeItem(SaleItem item) {
        if (item == null) return;
        this.items.remove(item);
        item.setSale(null);
        recalcTotal();
    }

    public void recalcTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleItem it : items) {
            if (it.getTotalPrice() != null) {
                sum = sum.add(it.getTotalPrice());
            }
        }
        this.totalAmount = sum;
    }

    public void cancel() {
        this.status = SaleStatus.CANCELLED;
    }
}
