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
 * <p>Exemplos:</p>
 * <ul>
 *   <li>Entrada manual de mercadoria</li>
 *   <li>Baixa por venda</li>
 *   <li>Correção de inventário</li>
 *   <li>Reserva/liberação de estoque</li>
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
     *
     * <p>Convenção:
     * positivos geralmente aumentam saldo; negativos reduzem saldo.</p>
     */
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    private InventoryMovementType movementType;

    /**
     * Tipo da referência que originou a movimentação.
     *
     * <p>Ex.: SALE, MANUAL_ADJUSTMENT, RETURN, IMPORT</p>
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}