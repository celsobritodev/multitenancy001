package brito.com.multitenancy001.controlplane.billing.domain;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Entidade de pagamento do Control Plane.
 *
 * <p>Esta entidade representa cobranças/pagamentos vinculados a uma conta,
 * incluindo metadados comerciais de assinatura/plano quando aplicável.</p>
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_account", columnList = "account_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_date", columnList = "payment_date"),
        @Index(name = "idx_payment_target_plan", columnList = "target_plan"),
        @Index(name = "idx_payment_purpose", columnList = "payment_purpose")
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

    /**
     * Conta dona do pagamento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * Valor efetivo cobrado.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /**
     * Data/hora do pagamento/cobrança.
     */
    @Column(name = "payment_date", nullable = false, columnDefinition = "timestamptz")
    private Instant paymentDate;

    /**
     * Validade comercial do pagamento.
     */
    @Column(name = "valid_until", columnDefinition = "timestamptz")
    private Instant validUntil;

    /**
     * Status do pagamento.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Identificador externo/interno da transação.
     */
    @Column(name = "transaction_id", unique = true, length = 100)
    private String transactionId;

    /**
     * Método de pagamento.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    /**
     * Gateway de pagamento.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_gateway", nullable = false, length = 50)
    private PaymentGateway paymentGateway;

    /**
     * Moeda.
     */
    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "BRL";

    /**
     * Descrição funcional.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Metadados livres.
     */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    /**
     * URL de invoice.
     */
    @Column(name = "invoice_url", columnDefinition = "TEXT")
    private String invoiceUrl;

    /**
     * URL de recibo.
     */
    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;

    /**
     * Plano alvo vinculado ao pagamento, quando aplicável.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_plan", length = 40)
    private SubscriptionPlan targetPlan;

    /**
     * Ciclo de cobrança do pagamento.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 20)
    private BillingCycle billingCycle;

    /**
     * Finalidade do pagamento.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_purpose", length = 40)
    @Builder.Default
    private PaymentPurpose paymentPurpose = PaymentPurpose.OTHER;

    /**
     * Snapshot do preço do plano no momento da cobrança.
     */
    @Column(name = "plan_price_snapshot", precision = 14, scale = 2)
    private BigDecimal planPriceSnapshot;

    /**
     * Data efetiva do início da cobertura.
     */
    @Column(name = "effective_from", columnDefinition = "timestamptz")
    private Instant effectiveFrom;

    /**
     * Data final da cobertura comercial.
     */
    @Column(name = "coverage_end_date", columnDefinition = "timestamptz")
    private Instant coverageEndDate;

    /**
     * Auditoria padrão.
     */
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    /**
     * Data/hora do reembolso.
     */
    @Column(name = "refunded_at", columnDefinition = "timestamptz")
    private Instant refundedAt;

    /**
     * Valor reembolsado.
     */
    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    /**
     * Motivo do reembolso.
     */
    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    /**
     * Callback de persistência inicial.
     */
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

        if (this.paymentPurpose == null) {
            this.paymentPurpose = PaymentPurpose.OTHER;
        }

        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "BRL";
        }

        if (this.coverageEndDate != null && this.validUntil == null) {
            this.validUntil = this.coverageEndDate;
        }
    }

    /**
     * Calcula validade default.
     *
     * @param baseDate data base
     * @return validade default
     */
    private Instant calculateDefaultValidUntil(Instant baseDate) {
        return baseDate.plus(30, ChronoUnit.DAYS);
    }

    /**
     * Marca pagamento como concluído.
     *
     * @param now instante atual
     */
    public void markAsCompleted(Instant now) {
        this.status = PaymentStatus.COMPLETED;

        if (this.paymentDate == null) {
            this.paymentDate = now;
        }

        if (this.effectiveFrom == null) {
            this.effectiveFrom = now;
        }

        if (this.coverageEndDate != null) {
            this.validUntil = this.coverageEndDate;
        } else if (this.validUntil == null) {
            this.validUntil = calculateDefaultValidUntil(this.paymentDate);
            this.coverageEndDate = this.validUntil;
        } else if (this.coverageEndDate == null) {
            this.coverageEndDate = this.validUntil;
        }
    }

    /**
     * Marca pagamento como falho.
     *
     * @param reason motivo
     */
    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        if (this.metadataJson == null) {
            this.metadataJson = "{\"failure_reason\":\"" + reason + "\"}";
        }
    }

    /**
     * Indica se o pagamento pode ser reembolsado.
     *
     * @param now instante atual
     * @return true se pode
     */
    public boolean canBeRefunded(Instant now) {
        if (this.status != PaymentStatus.COMPLETED) return false;
        if (this.refundedAt != null) return false;
        if (this.paymentDate == null) return false;

        Instant limit = now.minus(90, ChronoUnit.DAYS);
        return this.paymentDate.isAfter(limit);
    }

    /**
     * Reembolso parcial.
     *
     * @param now instante atual
     * @param amount valor
     * @param reason motivo
     */
    public void refundPartially(Instant now, BigDecimal amount, String reason) {
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

    /**
     * Reembolso integral.
     *
     * @param now instante atual
     * @param reason motivo
     */
    public void refundFully(Instant now, String reason) {
        if (!canBeRefunded(now)) {
            throw new IllegalStateException("Pagamento não pode ser reembolsado");
        }

        this.refundAmount = this.amount;
        this.refundReason = reason;
        this.refundedAt = now;
        this.status = PaymentStatus.REFUNDED;
    }

    /**
     * Indica se este pagamento exige binding com subscription.
     *
     * @return true se exige binding
     */
    public boolean requiresPlanBinding() {
        return this.paymentPurpose == PaymentPurpose.PLAN_UPGRADE;
    }
}