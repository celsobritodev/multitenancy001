package brito.com.multitenancy001.entities.account;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_account", columnList = "account_id"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_transaction", columnList = "transaction_id", unique = true),
    @Index(name = "idx_payment_date", columnList = "payment_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    
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
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "payment_gateway", length = 50)
    private String paymentGateway;
    
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "BRL";
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    
    @Column(name = "invoice_url")
    private String invoiceUrl;
    
    @Column(name = "receipt_url")
    private String receiptUrl;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
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
            this.paymentDate = LocalDateTime.now();
        }

        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.status == PaymentStatus.COMPLETED && this.validUntil == null) {
            this.validUntil = calculateDefaultValidUntil();
        }

    }
    
    
  
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    private LocalDateTime calculateDefaultValidUntil() {
        return this.paymentDate.plusDays(30); // período default do ciclo
    }
    
    /**
     * Marca o pagamento como concluído
     */
    public void markAsCompleted() {
        this.status = PaymentStatus.COMPLETED;
        if (this.validUntil == null) {
            this.validUntil = calculateDefaultValidUntil();
        }
    }
    
    /**
     * Marca o pagamento como falhado
     */
    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        if (this.metadataJson == null) {
            this.metadataJson = "{\"failure_reason\":\"" + reason + "\"}";
        }
    }
    
    /**
     * Realiza reembolso parcial
     */
    public void refundPartially(BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("Valor de reembolso inválido");
        }
        
        this.refundAmount = amount;
        this.refundReason = reason;
        this.refundedAt = LocalDateTime.now();
        this.status = PaymentStatus.REFUNDED;
    }
    
    /**
     * Realiza reembolso total
     */
    public void refundFully(String reason) {
        this.refundAmount = this.amount;
        this.refundReason = reason;
        this.refundedAt = LocalDateTime.now();
        this.status = PaymentStatus.REFUNDED;
    }
    
    /**
     * Verifica se o pagamento está ativo (não expirado)
     */
    public boolean isActive() {
        return this.status == PaymentStatus.COMPLETED && 
               (this.validUntil == null || this.validUntil.isAfter(LocalDateTime.now()));
    }
    
    /**
     * Verifica se o pagamento pode ser reembolsado
     */
    public boolean canBeRefunded() {
        return this.status == PaymentStatus.COMPLETED && 
               this.refundedAt == null &&
               this.paymentDate.isAfter(LocalDateTime.now().minusDays(90)); // Reembolso até 90 dias
    }
}