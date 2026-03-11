package brito.com.multitenancy001.tenant.inventory.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.inventory.app.command.AdjustInventoryCommand;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovementType;
import brito.com.multitenancy001.tenant.inventory.persistence.InventoryMovementRepository;
import brito.com.multitenancy001.tenant.inventory.persistence.TenantInventoryRepository;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
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
 *   <li>Expor operações preparadas para integração com Sales.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantInventoryService {

    private final TenantInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final TenantProductRepository tenantProductRepository;
    private final AppClock clock;

    /**
     * Retorna o estoque atual do produto, criando um registro vazio se necessário.
     *
     * @param productId id do produto
     * @return estado atual do estoque
     */
    public InventoryItem getOrCreateInventory(UUID productId) {
        validateProductExists(productId);

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
        validateProductExists(productId);

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
     * @param command comando de ajuste
     * @return inventory atualizado
     */
    public InventoryItem adjustInventory(AdjustInventoryCommand command) {
        if (command == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "inventory command is required", 400);
        }
        if (command.getProductId() == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "productId is required", 400);
        }
        if (command.getQuantity() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "quantity is required", 400);
        }
        if (command.getMovementType() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "movementType is required", 400);
        }

        validateProductExists(command.getProductId());

        log.info(
                "Inventory adjust start | productId={} | quantity={} | movementType={} | referenceType={} | referenceId={}",
                command.getProductId(),
                command.getQuantity(),
                command.getMovementType(),
                command.getReferenceType(),
                command.getReferenceId()
        );

        InventoryItem item = inventoryRepository.findWithLockByProductId(command.getProductId())
                .orElseGet(() -> createInventory(command.getProductId()));

        BigDecimal currentAvailable = safe(item.getQuantityAvailable());
        BigDecimal currentReserved = safe(item.getQuantityReserved());

        validateStockRules(item, command);

        switch (command.getMovementType()) {
            case INBOUND, RETURN, ADJUSTMENT ->
                    item.setQuantityAvailable(currentAvailable.add(command.getQuantity()));

            case OUTBOUND ->
                    item.setQuantityAvailable(currentAvailable.add(command.getQuantity())); // quantity negativa

            case RESERVATION ->
                    item.setQuantityReserved(currentReserved.add(command.getQuantity()));

            case RELEASE_RESERVATION ->
                    item.setQuantityReserved(currentReserved.add(command.getQuantity())); // quantity negativa

            default ->
                    throw new IllegalStateException("Unsupported movementType: " + command.getMovementType());
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
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade a ser consumida
     * @return inventory atualizado
     */
    public InventoryItem consumeStockForSale(UUID saleId, UUID productId, BigDecimal quantity) {
        if (saleId == null) {
            throw new ApiException(ApiErrorCode.SALE_NOT_FOUND, "saleId is required", 400);
        }
        if (productId == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "productId is required", 400);
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale item quantity must be positive", 400);
        }

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
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade a retornar
     * @return inventory atualizado
     */
    public InventoryItem restoreStockFromSale(UUID saleId, UUID productId, BigDecimal quantity) {
        if (saleId == null) {
            throw new ApiException(ApiErrorCode.SALE_NOT_FOUND, "saleId is required", 400);
        }
        if (productId == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "productId is required", 400);
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "restore quantity must be positive", 400);
        }

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

    /**
     * Valida se o produto existe no tenant atual.
     */
    private void validateProductExists(UUID productId) {
        if (!tenantProductRepository.existsById(productId)) {
            throw new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "product not found", 404);
        }
    }

    /**
     * Regras de bloqueio de estoque negativo.
     */
    private void validateStockRules(InventoryItem item, AdjustInventoryCommand command) {
        BigDecimal available = safe(item.getQuantityAvailable());
        BigDecimal reserved = safe(item.getQuantityReserved());
        BigDecimal delta = safe(command.getQuantity());

        if (command.getMovementType() == InventoryMovementType.OUTBOUND) {
            BigDecimal resulting = available.add(delta);
            if (resulting.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(
                        ApiErrorCode.INSUFFICIENT_STOCK,
                        "insufficient stock for outbound movement",
                        409
                );
            }
        }

        if (command.getMovementType() == InventoryMovementType.RELEASE_RESERVATION) {
            BigDecimal resultingReserved = reserved.add(delta);
            if (resultingReserved.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(
                        ApiErrorCode.INVALID_REQUEST,
                        "cannot release more reserved stock than available",
                        400
                );
            }
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}