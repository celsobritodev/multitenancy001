package brito.com.multitenancy001.controlplane.domain.billing;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.persistence.audit.AuditEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_account", columnList = "account_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_date", columnList = "payment_date")
})
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_id", unique = true, length = 100)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_gateway", nullable = false, length = 50)
    private PaymentGateway paymentGateway;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "BRL";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "invoice_url", columnDefinition = "TEXT")
    private String invoiceUrl;

    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== AUDIT (ator)
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @PrePersist
    protected void onCreate() {
        if (this.transactionId == null) {
            this.transactionId = "PAY_" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();
        }

        if (this.paymentDate == null) {
            throw new IllegalStateException("paymentDate deve ser definido pela aplicação (Clock/AppClock).");
        }

        if (this.status == PaymentStatus.COMPLETED && this.validUntil == null) {
            this.validUntil = calculateDefaultValidUntil(this.paymentDate);
        }
    }

    private LocalDateTime calculateDefaultValidUntil(LocalDateTime baseDate) {
        return baseDate.plusDays(30);
    }

    public void markAsCompleted(LocalDateTime now) {
        this.status = PaymentStatus.COMPLETED;
        if (this.paymentDate == null) this.paymentDate = now;
        if (this.validUntil == null) this.validUntil = calculateDefaultValidUntil(this.paymentDate);
    }

    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        if (this.metadataJson == null) {
            this.metadataJson = "{\"failure_reason\":\"" + reason + "\"}";
        }
    }
    
    public boolean canBeRefunded(LocalDateTime now) {
        if (this.status != PaymentStatus.COMPLETED) return false;
        if (this.refundedAt != null) return false;
        if (this.paymentDate == null) return false;

        // Exemplo original: até 90 dias após paymentDate (regra determinística)
        // (equivalente a: paymentDate.isAfter(now.minusDays(90)))
        return this.paymentDate.isAfter(now.minusDays(90));
    }
    
    public void refundPartially(LocalDateTime now, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("Valor de reembolso inválido");
        }
        if (!canBeRefunded(now)) {
            throw new IllegalStateException("Pagamento não pode ser reembolsado");
        }

        this.refundAmount = amount;
        this.refundReason = reason;
        this.refundedAt = now;
        this.status = PaymentStatus.REFUNDED;
    }

    public void refundFully(LocalDateTime now, String reason) {
        if (!canBeRefunded(now)) {
            throw new IllegalStateException("Pagamento não pode ser reembolsado");
        }

        this.refundAmount = this.amount;
        this.refundReason = reason;
        this.refundedAt = now;
        this.status = PaymentStatus.REFUNDED;
    }
}
