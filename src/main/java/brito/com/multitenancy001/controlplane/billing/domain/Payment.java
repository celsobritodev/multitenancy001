package brito.com.multitenancy001.controlplane.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Entidade de pagamento do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Representar a cobrança/pagamento vinculada a uma conta.</li>
 *   <li>Manter metadados de billing binding quando o pagamento envolver plano.</li>
 *   <li>Aplicar invariantes de transição de estado.</li>
 *   <li>Persistir chave de idempotência para retry-safe real.</li>
 * </ul>
 *
 * <p>Regras importantes:</p>
 * <ul>
 *   <li>{@code PENDING -> COMPLETED} é permitido.</li>
 *   <li>{@code PENDING -> FAILED} é permitido.</li>
 *   <li>{@code COMPLETED -> COMPLETED} é idempotente.</li>
 *   <li>{@code FAILED -> FAILED} é idempotente.</li>
 *   <li>Não é permitido concluir pagamento FAILED.</li>
 *   <li>Não é permitido falhar pagamento COMPLETED.</li>
 * </ul>
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_account", columnList = "account_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_date", columnList = "payment_date"),
        @Index(name = "idx_payment_target_plan", columnList = "target_plan"),
        @Index(name = "idx_payment_purpose", columnList = "payment_purpose"),
        @Index(name = "idx_payment_idempotency_key", columnList = "idempotency_key")
})
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
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
     *
     * <p>Este campo é usado no projeto como o timestamp principal do pagamento.</p>
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
     * Chave opcional de idempotência.
     *
     * <p>Usada para impedir duplicação de cobrança em retries equivalentes.</p>
     */
    @Column(name = "idempotency_key", unique = true, length = 160)
    private String idempotencyKey;

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
            this.transactionId = "PAY_" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();
        }

        if (this.paymentDate == null) {
            throw new IllegalStateException("paymentDate deve ser definido pela aplicação");
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

        if (this.idempotencyKey != null && this.idempotencyKey.isBlank()) {
            this.idempotencyKey = null;
        }
    }

    /**
     * Indica se este pagamento exige binding com subscription.
     *
     * @return true se exige binding
     */
    public boolean requiresPlanBinding() {
        return this.paymentPurpose == PaymentPurpose.PLAN_UPGRADE;
    }

    /**
     * Indica se o pagamento já foi finalizado com sucesso.
     *
     * @return true quando status for COMPLETED
     */
    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    /**
     * Indica se o pagamento falhou.
     *
     * @return true quando status for FAILED
     */
    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    /**
     * Indica se o pagamento ainda está pendente.
     *
     * @return true quando status for PENDING
     */
    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    /**
     * Marca pagamento como concluído.
     *
     * <p>Transições permitidas:</p>
     * <ul>
     *   <li>PENDING -> COMPLETED</li>
     *   <li>COMPLETED -> COMPLETED (idempotente)</li>
     * </ul>
     *
     * @param now instante atual
     */
    public void markAsCompleted(Instant now) {
        if (this.status == PaymentStatus.COMPLETED) {
            return;
        }

        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Pagamento não pode ser concluído a partir do status " + this.status
            );
        }

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

        log.info("Pagamento marcado como COMPLETED. paymentId={}, transactionId={}",
                this.id, this.transactionId);
    }

    /**
     * Marca pagamento como falho.
     *
     * <p>Transições permitidas:</p>
     * <ul>
     *   <li>PENDING -> FAILED</li>
     *   <li>FAILED -> FAILED (idempotente)</li>
     * </ul>
     *
     * @param reason motivo
     */
    public void markAsFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) {
            return;
        }

        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Pagamento não pode falhar a partir do status " + this.status
            );
        }

        this.status = PaymentStatus.FAILED;

        if (this.metadataJson == null) {
            this.metadataJson = "{\"failure_reason\":\"" + reason + "\"}";
        }

        log.warn("Pagamento marcado como FAILED. paymentId={}, transactionId={}, reason={}",
                this.id, this.transactionId, reason);
    }

    /**
     * Indica se o pagamento pode ser reembolsado.
     *
     * @param now instante atual
     * @return true se pode
     */
    public boolean canBeRefunded(Instant now) {
        if (this.status != PaymentStatus.COMPLETED) {
            return false;
        }
        if (this.refundedAt != null) {
            return false;
        }
        if (this.paymentDate == null) {
            return false;
        }

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
     * Calcula validade default.
     *
     * @param baseDate data base
     * @return validade default
     */
    private Instant calculateDefaultValidUntil(Instant baseDate) {
        return baseDate.plus(30, ChronoUnit.DAYS);
    }
}