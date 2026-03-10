package brito.com.multitenancy001.tenant.sales.domain;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade de venda do contexto TENANT.
 *
 * <p>Integração com customers:</p>
 * <ul>
 *   <li>{@code customerId} representa o vínculo lógico com o customer cadastrado.</li>
 *   <li>Os campos {@code customerName}, {@code customerDocument},
 *       {@code customerEmail} e {@code customerPhone} armazenam snapshot
 *       do customer no momento da venda.</li>
 * </ul>
 *
 * <p>Assim, mesmo que os dados do customer mudem depois, a venda continua
 * preservando o histórico do momento em que foi emitida.</p>
 */
@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_sale_date", columnList = "sale_date"),
        @Index(name = "idx_sales_status", columnList = "status"),
        @Index(name = "idx_sales_customer_id", columnList = "customer_id")
})
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "items")
@Slf4j
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
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Referência lógica ao customer cadastrado.
     */
    @Column(name = "customer_id", columnDefinition = "uuid")
    private UUID customerId;

    /**
     * Snapshot do customer no momento da venda.
     */
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

    /**
     * Soft delete padrão do projeto.
     *
     * <p>Não recebe tempo no domínio. O listener centraliza a auditoria.</p>
     */
    public void softDelete() {
        if (this.deleted) {
            log.debug("Sale já estava deletada. id={}", this.id);
            return;
        }
        this.deleted = true;
        log.info("Soft delete aplicado em sale. id={}", this.id);
    }

    /**
     * Compatibilidade com chamadas antigas.
     *
     * @param ignoredNow parâmetro ignorado; auditoria é responsabilidade do listener
     */
    public void softDelete(Instant ignoredNow) {
        softDelete();
    }

    /**
     * Restaura uma venda deletada logicamente.
     */
    public void restore() {
        if (!this.deleted) {
            log.debug("Sale não estava deletada. id={}", this.id);
            return;
        }

        this.deleted = false;
        this.audit.clearDeleted();
        log.info("Sale restaurada com sucesso. id={}", this.id);
    }

    /**
     * Adiciona item à venda e recalcula o total.
     */
    public void addItem(SaleItem item) {
        if (item == null) return;
        item.setSale(this);
        this.items.add(item);
        recalcTotal();
    }

    /**
     * Remove item da venda e recalcula o total.
     */
    public void removeItem(SaleItem item) {
        if (item == null) return;
        this.items.remove(item);
        item.setSale(null);
        recalcTotal();
    }

    /**
     * Recalcula o valor total da venda com base nos itens não deletados.
     */
    public void recalcTotal() {
        BigDecimal sum = BigDecimal.ZERO;

        if (items != null) {
            for (SaleItem it : items) {
                if (it == null) continue;
                if (it.isDeleted()) continue;
                if (it.getTotalPrice() != null) {
                    sum = sum.add(it.getTotalPrice());
                }
            }
        }

        this.totalAmount = sum;
        log.debug("Total recalculado para sale id={} total={}", this.id, this.totalAmount);
    }

    /**
     * Cancela a venda.
     */
    public void cancel() {
        this.status = SaleStatus.CANCELLED;
        log.info("Sale cancelada. id={}", this.id);
    }
}