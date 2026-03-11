package brito.com.multitenancy001.tenant.inventory.persistence;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA do saldo de estoque por produto.
 *
 * <p>Observação:
 * o método com lock pessimista é usado em fluxos críticos
 * para reduzir risco de overselling em operações concorrentes.</p>
 */
public interface TenantInventoryRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductId(UUID productId);

    /**
     * Busca inventory item do produto com lock pessimista.
     *
     * @param productId id do produto
     * @return inventory item opcional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> findWithLockByProductId(UUID productId);
}