package brito.com.multitenancy001.tenant.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representa o saldo consolidado de estoque de um produto no tenant.
 *
 * <p>Campos principais:</p>
 * <ul>
 *   <li>{@code quantityAvailable}: saldo fisicamente disponível.</li>
 *   <li>{@code quantityReserved}: saldo reservado para fluxos futuros.</li>
 *   <li>{@code minStock}: ponto mínimo para alerta operacional.</li>
 * </ul>
 *
 * <p>Observação importante para concorrência:</p>
 * <ul>
 *   <li>Esta entidade possui {@link Version} para ajudar na proteção contra
 *   lost updates em cenários concorrentes.</li>
 * </ul>
 */
@Entity
@Table(
        name = "inventory_items",
        indexes = {
                @Index(name = "idx_inventory_items_product_id", columnList = "product_id", unique = true)
        }
)
@Getter
@Setter
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id do produto no módulo Products.
     */
    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    /**
     * Quantidade disponível para uso imediato.
     */
    @Column(name = "quantity_available", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityAvailable;

    /**
     * Quantidade reservada para fluxos futuros.
     */
    @Column(name = "quantity_reserved", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityReserved;

    /**
     * Estoque mínimo desejado para alertas.
     */
    @Column(name = "min_stock", nullable = false, precision = 19, scale = 4)
    private BigDecimal minStock;

    /**
     * Controle de versão otimista para reduzir risco de lost update
     * em cenários concorrentes.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Indica se o estoque disponível está abaixo do mínimo configurado.
     *
     * @return true quando quantityAvailable < minStock
     */
    public boolean isLowStock() {
        BigDecimal available = quantityAvailable != null ? quantityAvailable : BigDecimal.ZERO;
        BigDecimal minimum = minStock != null ? minStock : BigDecimal.ZERO;
        return available.compareTo(minimum) < 0;
    }
}