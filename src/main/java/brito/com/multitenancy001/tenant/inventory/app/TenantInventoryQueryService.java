package brito.com.multitenancy001.tenant.inventory.app;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;
import brito.com.multitenancy001.tenant.inventory.persistence.InventoryMovementRepository;
import brito.com.multitenancy001.tenant.inventory.persistence.TenantInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Query service do módulo de inventory.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Obter inventory atual do produto.</li>
 *   <li>Criar inventory zerado quando ainda não existir.</li>
 *   <li>Listar histórico de movimentações do ledger.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantInventoryQueryService {

    private final TenantInventoryRepository tenanntInventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final TenantInventoryHelper tenantInventoryHelper;

    /**
     * Retorna o estoque atual do produto, criando um registro zerado se necessário.
     *
     * @param productId id do produto
     * @return estado atual do estoque
     */
    public InventoryItem getOrCreateInventory(UUID productId) {
        tenantInventoryHelper.validateProductExists(productId);

        log.debug("INVENTORY_GET_OR_CREATE_START | productId={}", productId);

        InventoryItem item = tenanntInventoryRepository.findByProductId(productId)
                .orElseGet(() -> tenantInventoryHelper.createInventory(productId));

        log.debug(
                "INVENTORY_GET_OR_CREATE_FINISH | productId={} | inventoryId={} | available={} | reserved={} | minStock={}",
                productId,
                item.getId(),
                item.getQuantityAvailable(),
                item.getQuantityReserved(),
                item.getMinStock()
        );

        return item;
    }

    /**
     * Lista o histórico de movimentações do produto em ordem decrescente de criação.
     *
     * @param productId id do produto
     * @return lista de movimentações
     */
    public List<InventoryMovement> listMovementsByProduct(UUID productId) {
        tenantInventoryHelper.validateProductExists(productId);

        log.info("INVENTORY_MOVEMENT_LIST_START | productId={}", productId);

        List<InventoryMovement> movements = inventoryMovementRepository.findByProductIdOrderByCreatedAtDesc(productId);

        log.info(
                "INVENTORY_MOVEMENT_LIST_FINISH | productId={} | totalMovements={} | sample={}",
                productId,
                movements.size(),
                tenantInventoryHelper.describeMovements(movements, 5)
        );

        return movements;
    }
}