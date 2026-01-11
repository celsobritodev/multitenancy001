package brito.com.multitenancy001.tenant.domain.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate;

    @OneToMany(
            mappedBy = "sale",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SaleStatus status;

    public enum SaleStatus {
        PENDING, COMPLETED, CANCELLED, REFUNDED
    }

    // ✅ helpers opcionais (mantém consistência do agregado)
    public void addItem(SaleItem item) {
        if (item == null) return;
        items.add(item);
        item.setSale(this);
    }

    public void removeItem(SaleItem item) {
        if (item == null) return;
        items.remove(item);
        item.setSale(null);
    }
}
