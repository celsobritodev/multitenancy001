package brito.com.multitenancy001.tenant.inventory.domain;

/**
 * Tipos de movimentação de estoque.
 *
 * <p>Convenção:</p>
 * <ul>
 *   <li>INBOUND: aumenta estoque disponível.</li>
 *   <li>OUTBOUND: reduz estoque disponível.</li>
 *   <li>ADJUSTMENT: ajuste manual positivo ou negativo.</li>
 *   <li>RETURN: devolução que retorna ao estoque.</li>
 *   <li>RESERVATION: reserva de estoque para operação futura.</li>
 *   <li>RELEASE_RESERVATION: libera reserva anteriormente criada.</li>
 * </ul>
 */
public enum InventoryMovementType {

    INBOUND,
    OUTBOUND,
    ADJUSTMENT,
    RETURN,
    RESERVATION,
    RELEASE_RESERVATION
}