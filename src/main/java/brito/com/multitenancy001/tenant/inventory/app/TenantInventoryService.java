package brito.com.multitenancy001.tenant.inventory.app;

import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.inventory.app.command.AdjustInventoryCommand;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovementType;
import brito.com.multitenancy001.tenant.inventory.persistence.InventoryMovementRepository;
import brito.com.multitenancy001.tenant.inventory.persistence.TenantInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application service responsável pelas regras de negócio de estoque.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar registro de inventory quando ainda não existir.</li>
 *   <li>Aplicar ajustes de quantidade disponível.</li>
 *   <li>Aplicar reservas/liberação de reservas.</li>
 *   <li>Registrar histórico de movimentações.</li>
 *   <li>Expor operações preparadas para futura integração com Sales.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantInventoryService {

    private final TenantInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final AppClock clock;

    /**
     * Retorna o estoque atual do produto, criando um registro vazio se necessário.
     *
     * @param productId id do produto
     * @return estado atual do estoque
     */
    public InventoryItem getOrCreateInventory(UUID productId) {

        log.debug("Inventory getOrCreate start | productId={}", productId);

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> createInventory(productId));

        log.debug(
                "Inventory getOrCreate finished | productId={} | available={} | reserved={}",
                productId,
                item.getQuantityAvailable(),
                item.getQuantityReserved()
        );

        return item;
    }

    /**
     * Busca o histórico de movimentações do produto em ordem mais recente primeiro.
     *
     * @param productId id do produto
     * @return lista de movimentações
     */
    public List<InventoryMovement> listMovementsByProduct(UUID productId) {

        log.info("Inventory movement list start | productId={}", productId);

        List<InventoryMovement> movements = movementRepository.findByProductIdOrderByCreatedAtDesc(productId);

        log.info(
                "Inventory movement list finished | productId={} | totalMovements={}",
                productId,
                movements.size()
        );

        return movements;
    }

    /**
     * Ajusta estoque com base em um comando manual ou sistêmico.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>RESERVATION aumenta quantityReserved.</li>
     *   <li>RELEASE_RESERVATION reduz quantityReserved.</li>
     *   <li>OUTBOUND reduz quantityAvailable.</li>
     *   <li>INBOUND/RETURN aumentam quantityAvailable.</li>
     *   <li>ADJUSTMENT aplica delta direto em quantityAvailable.</li>
     * </ul>
     *
     * @param command comando de ajuste
     * @return inventory atualizado
     */
    public InventoryItem adjustInventory(AdjustInventoryCommand command) {

        log.info(
                "Inventory adjust start | productId={} | quantity={} | movementType={} | referenceType={} | referenceId={}",
                command.getProductId(),
                command.getQuantity(),
                command.getMovementType(),
                command.getReferenceType(),
                command.getReferenceId()
        );

        InventoryItem item = getOrCreateInventory(command.getProductId());

        BigDecimal currentAvailable = safe(item.getQuantityAvailable());
        BigDecimal currentReserved = safe(item.getQuantityReserved());

        switch (command.getMovementType()) {
            case INBOUND, RETURN, ADJUSTMENT -> item.setQuantityAvailable(currentAvailable.add(command.getQuantity()));
            case OUTBOUND -> item.setQuantityAvailable(currentAvailable.add(command.getQuantity())); // esperado quantity negativo
            case RESERVATION -> item.setQuantityReserved(currentReserved.add(command.getQuantity()));
            case RELEASE_RESERVATION -> item.setQuantityReserved(currentReserved.add(command.getQuantity())); // esperado quantity negativo
            default -> throw new IllegalStateException("Unsupported movementType: " + command.getMovementType());
        }

        item.setUpdatedAt(clock.instant());

        InventoryItem savedItem = inventoryRepository.save(item);

        registerMovement(command);

        log.info(
                "Inventory adjust finished | productId={} | availableBefore={} | reservedBefore={} | availableAfter={} | reservedAfter={}",
                command.getProductId(),
                currentAvailable,
                currentReserved,
                savedItem.getQuantityAvailable(),
                savedItem.getQuantityReserved()
        );

        return savedItem;
    }

    /**
     * Consome estoque disponível por item de venda.
     *
     * <p>Método preparado para futura integração com Sales.</p>
     *
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade a ser consumida
     * @return inventory atualizado
     */
    public InventoryItem consumeStockForSale(UUID saleId, UUID productId, BigDecimal quantity) {

        log.info(
                "Consume stock for sale start | saleId={} | productId={} | quantity={}",
                saleId,
                productId,
                quantity
        );

        AdjustInventoryCommand command = AdjustInventoryCommand.builder()
                .productId(productId)
                .quantity(quantity.negate())
                .movementType(InventoryMovementType.OUTBOUND)
                .referenceType("SALE")
                .referenceId(String.valueOf(saleId))
                .notes("Automatic outbound movement generated by sale")
                .build();

        InventoryItem item = adjustInventory(command);

        log.info(
                "Consume stock for sale finished | saleId={} | productId={} | newAvailable={}",
                saleId,
                productId,
                item.getQuantityAvailable()
        );

        return item;
    }

    /**
     * Devolve estoque anteriormente consumido por venda.
     *
     * <p>Método preparado para futura integração com Sales canceladas/estornadas.</p>
     *
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade a retornar
     * @return inventory atualizado
     */
    public InventoryItem restoreStockFromSale(UUID saleId, UUID productId, BigDecimal quantity) {

        log.info(
                "Restore stock from sale start | saleId={} | productId={} | quantity={}",
                saleId,
                productId,
                quantity
        );

        AdjustInventoryCommand command = AdjustInventoryCommand.builder()
                .productId(productId)
                .quantity(quantity)
                .movementType(InventoryMovementType.RETURN)
                .referenceType("SALE_CANCEL")
                .referenceId(String.valueOf(saleId))
                .notes("Automatic stock restoration generated by sale cancellation/reversal")
                .build();

        InventoryItem item = adjustInventory(command);

        log.info(
                "Restore stock from sale finished | saleId={} | productId={} | newAvailable={}",
                saleId,
                productId,
                item.getQuantityAvailable()
        );

        return item;
    }

    /**
     * Cria o registro inicial de inventory para um produto.
     */
    private InventoryItem createInventory(UUID productId) {

        log.info("Creating inventory item | productId={}", productId);

        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setQuantityAvailable(BigDecimal.ZERO);
        item.setQuantityReserved(BigDecimal.ZERO);
        item.setMinStock(BigDecimal.ZERO);
        item.setCreatedAt(clock.instant());
        item.setUpdatedAt(clock.instant());

        InventoryItem saved = inventoryRepository.save(item);

        log.info("Inventory item created | inventoryId={} | productId={}", saved.getId(), productId);

        return saved;
    }

    /**
     * Registra uma movimentação no histórico.
     */
    private void registerMovement(AdjustInventoryCommand command) {

        log.info(
                "Registering inventory movement | productId={} | quantity={} | movementType={} | referenceType={} | referenceId={}",
                command.getProductId(),
                command.getQuantity(),
                command.getMovementType(),
                command.getReferenceType(),
                command.getReferenceId()
        );

        InventoryMovement movement = new InventoryMovement();
        movement.setProductId(command.getProductId());
        movement.setQuantity(command.getQuantity());
        movement.setMovementType(command.getMovementType());
        movement.setReferenceType(command.getReferenceType());
        movement.setReferenceId(command.getReferenceId());
        movement.setNotes(command.getNotes());
        movement.setCreatedAt(clock.instant());

        InventoryMovement savedMovement = movementRepository.save(movement);

        log.info(
                "Inventory movement registered | movementId={} | productId={} | movementType={}",
                savedMovement.getId(),
                savedMovement.getProductId(),
                savedMovement.getMovementType()
        );
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}