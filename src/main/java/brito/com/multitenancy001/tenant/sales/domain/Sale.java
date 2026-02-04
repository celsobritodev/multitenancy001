package brito.com.multitenancy001.tenant.sales.domain;

import jakarta.persistence.*;
import lombok.*;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_sale_date", columnList = "sale_date"),
        @Index(name = "idx_sales_status", columnList = "status")
})
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "items")
public class Sale implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /**
     * Instante real: quando a venda ocorreu.
     * (DB: TIMESTAMPTZ)
     */
    @Column(name = "sale_date", nullable = false, columnDefinition = "timestamptz")
    private Instant saleDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

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

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void markDeleted() {
        this.deleted = true;
    }

    public void softDelete(Instant now) {
        if (this.deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.audit.markDeleted(now);
    }

    public void restore() {
        if (!this.deleted) return;

        this.deleted = false;
        this.audit.clearDeleted();
    }

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

