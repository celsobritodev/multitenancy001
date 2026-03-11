package brito.com.multitenancy001.tenant.inventory.persistence;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA do saldo de estoque por produto.
 */
public interface TenantInventoryRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductId(UUID productId);
}