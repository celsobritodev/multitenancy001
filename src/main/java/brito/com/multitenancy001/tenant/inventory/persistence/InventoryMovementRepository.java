package brito.com.multitenancy001.tenant.inventory.persistence;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository JPA do histórico de movimentações de estoque.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByProductIdOrderByCreatedAtDesc(UUID productId);
}