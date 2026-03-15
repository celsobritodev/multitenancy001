package brito.com.multitenancy001.tenant.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Histórico imutável de movimentações de estoque.
 *
 * <p>Esta entidade representa o ledger operacional do estoque.</p>
 *
 * <p>Exemplos de origem:</p>
 * <ul>
 *   <li>entrada manual de mercadoria</li>
 *   <li>baixa por venda</li>
 *   <li>devolução</li>
 *   <li>ajuste operacional</li>
 *   <li>reserva/liberação</li>
 * </ul>
 *
 * <p>Convenção de quantidade:</p>
 * <ul>
 *   <li>positivos normalmente aumentam saldo</li>
 *   <li>negativos normalmente reduzem saldo</li>
 * </ul>
 *
 * <p>Para reconciliação ledger:</p>
 * <ul>
 *   <li>O ideal é que cada movimento seja persistido exatamente uma vez.</li>
 *   <li>O conjunto de movimentos deve explicar o saldo atual do {@link InventoryItem}.</li>
 * </ul>
 */
@Entity
@Table(
        name = "inventory_movements",
        indexes = {
                @Index(name = "idx_inventory_movements_product_id", columnList = "product_id"),
                @Index(name = "idx_inventory_movements_created_at", columnList = "created_at"),
                @Index(name = "idx_inventory_movements_product_created", columnList = "product_id, created_at")
        }
)
@Getter
@Setter
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Produto impactado pela movimentação.
     */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /**
     * Quantidade movimentada.
     */
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /**
     * Tipo funcional da movimentação.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    private InventoryMovementType movementType;

    /**
     * Tipo da referência que originou a movimentação.
     *
     * <p>Ex.: SALE, SALE_CANCEL, MANUAL_ADJUSTMENT, RETURN.</p>
     */
    @Column(name = "reference_type", length = 40)
    private String referenceType;

    /**
     * Id textual da referência de origem.
     */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /**
     * Observação livre para troubleshooting operacional.
     */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Data/hora de criação do movimento.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}