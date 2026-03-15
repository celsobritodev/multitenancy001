package brito.com.multitenancy001.tenant.inventory.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
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
 *   <li>Aplicar reservas e liberações de reserva.</li>
 *   <li>Registrar histórico imutável de movimentações.</li>
 *   <li>Expor operações prontas para integração com Sales.</li>
 * </ul>
 *
 * <p>Características importantes:</p>
 * <ul>
 *   <li>Operações de mutação rodam em transação tenant.</li>
 *   <li>Leitura para ajuste usa lock do registro de inventory.</li>
 *   <li>Os logs foram reforçados para análise de race, ledger mismatch e double-spend.</li>
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
     * Retorna o estoque atual do produto, criando um registro zerado se necessário.
     *
     * @param productId id do produto
     * @return estado atual do estoque
     */
    public InventoryItem getOrCreateInventory(UUID productId) {
        validateProductExists(productId);

        log.debug("INVENTORY_GET_OR_CREATE_START | productId={}", productId);

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> createInventory(productId));

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
        validateProductExists(productId);

        log.info("INVENTORY_MOVEMENT_LIST_START | productId={}", productId);

        List<InventoryMovement> movements = movementRepository.findByProductIdOrderByCreatedAtDesc(productId);

        log.info(
                "INVENTORY_MOVEMENT_LIST_FINISH | productId={} | totalMovements={} | sample={}",
                productId,
                movements.size(),
                describeMovements(movements, 5)
        );

        return movements;
    }

    /**
     * Ajusta estoque com base em um comando manual ou sistêmico.
     *
     * <p>Fluxo:</p>
     * <ol>
     *   <li>Valida payload.</li>
     *   <li>Valida existência do produto.</li>
     *   <li>Busca inventory com lock.</li>
     *   <li>Valida regra de sinal e estoque negativo.</li>
     *   <li>Aplica delta no saldo.</li>
     *   <li>Persiste inventory.</li>
     *   <li>Registra movimento no ledger.</li>
     * </ol>
     *
     * @param command comando de ajuste
     * @return inventory atualizado
     */
    @TenantTx
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
                "INVENTORY_ADJUST_START | productId={} | quantity={} | movementType={} | referenceType={} | referenceId={} | notes={}",
                command.getProductId(),
                command.getQuantity(),
                command.getMovementType(),
                command.getReferenceType(),
                command.getReferenceId(),
                command.getNotes()
        );

        InventoryItem item = inventoryRepository.findWithLockByProductId(command.getProductId())
                .orElseGet(() -> createInventory(command.getProductId()));

        BigDecimal currentAvailable = safe(item.getQuantityAvailable());
        BigDecimal currentReserved = safe(item.getQuantityReserved());

        log.debug(
                "INVENTORY_ADJUST_LOCKED | inventoryId={} | productId={} | availableBefore={} | reservedBefore={} | movementType={} | delta={}",
                item.getId(),
                item.getProductId(),
                currentAvailable,
                currentReserved,
                command.getMovementType(),
                command.getQuantity()
        );

        validateStockRules(item, command);

        switch (command.getMovementType()) {
            case INBOUND, RETURN, ADJUSTMENT ->
                    item.setQuantityAvailable(currentAvailable.add(command.getQuantity()));

            case OUTBOUND ->
                    item.setQuantityAvailable(currentAvailable.add(command.getQuantity()));

            case RESERVATION ->
                    item.setQuantityReserved(currentReserved.add(command.getQuantity()));

            case RELEASE_RESERVATION ->
                    item.setQuantityReserved(currentReserved.add(command.getQuantity()));

            default ->
                    throw new IllegalStateException("Unsupported movementType: " + command.getMovementType());
        }

        item.setUpdatedAt(clock.instant());

        log.debug(
                "INVENTORY_ADJUST_PRE_SAVE | inventoryId={} | productId={} | availableAfter={} | reservedAfter={}",
                item.getId(),
                item.getProductId(),
                item.getQuantityAvailable(),
                item.getQuantityReserved()
        );

        InventoryItem savedItem = inventoryRepository.save(item);

        registerMovement(command);

        log.info(
                "INVENTORY_ADJUST_FINISH | productId={} | inventoryId={} | availableBefore={} | reservedBefore={} | availableAfter={} | reservedAfter={} | movementType={} | delta={}",
                command.getProductId(),
                savedItem.getId(),
                currentAvailable,
                currentReserved,
                savedItem.getQuantityAvailable(),
                savedItem.getQuantityReserved(),
                command.getMovementType(),
                command.getQuantity()
        );

        return savedItem;
    }

    /**
     * Consome estoque por item de venda.
     *
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade positiva a ser consumida
     * @return inventory atualizado
     */
    @TenantTx
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
                "INVENTORY_CONSUME_FOR_SALE_START | saleId={} | productId={} | requestedQuantity={}",
                saleId,
                productId,
                quantity
        );

        InventoryItem before = getOrCreateInventory(productId);

        log.debug(
                "INVENTORY_CONSUME_FOR_SALE_BEFORE | saleId={} | productId={} | available={} | reserved={}",
                saleId,
                productId,
                before.getQuantityAvailable(),
                before.getQuantityReserved()
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
                "INVENTORY_CONSUME_FOR_SALE_FINISH | saleId={} | productId={} | consumed={} | newAvailable={} | newReserved={}",
                saleId,
                productId,
                quantity,
                item.getQuantityAvailable(),
                item.getQuantityReserved()
        );

        return item;
    }

    /**
     * Devolve estoque anteriormente consumido por venda.
     *
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade positiva a devolver
     * @return inventory atualizado
     */
    @TenantTx
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
                "INVENTORY_RESTORE_FROM_SALE_START | saleId={} | productId={} | quantity={}",
                saleId,
                productId,
                quantity
        );

        InventoryItem before = getOrCreateInventory(productId);

        log.debug(
                "INVENTORY_RESTORE_FROM_SALE_BEFORE | saleId={} | productId={} | available={} | reserved={}",
                saleId,
                productId,
                before.getQuantityAvailable(),
                before.getQuantityReserved()
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
                "INVENTORY_RESTORE_FROM_SALE_FINISH | saleId={} | productId={} | restored={} | newAvailable={} | newReserved={}",
                saleId,
                productId,
                quantity,
                item.getQuantityAvailable(),
                item.getQuantityReserved()
        );

        return item;
    }

    /**
     * Cria o registro inicial de inventory para um produto.
     *
     * @param productId id do produto
     * @return inventory persistido
     */
    private InventoryItem createInventory(UUID productId) {
        log.info("INVENTORY_CREATE_START | productId={}", productId);

        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setQuantityAvailable(BigDecimal.ZERO);
        item.setQuantityReserved(BigDecimal.ZERO);
        item.setMinStock(BigDecimal.ZERO);
        item.setCreatedAt(clock.instant());
        item.setUpdatedAt(clock.instant());

        InventoryItem saved = inventoryRepository.save(item);

        log.info(
                "INVENTORY_CREATE_FINISH | inventoryId={} | productId={} | available={} | reserved={} | minStock={}",
                saved.getId(),
                productId,
                saved.getQuantityAvailable(),
                saved.getQuantityReserved(),
                saved.getMinStock()
        );

        return saved;
    }

    /**
     * Registra uma movimentação imutável no ledger de inventory.
     *
     * @param command comando que originou a movimentação
     */
    private void registerMovement(AdjustInventoryCommand command) {
        log.info(
                "INVENTORY_MOVEMENT_REGISTER_START | productId={} | quantity={} | movementType={} | referenceType={} | referenceId={}",
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
                "INVENTORY_MOVEMENT_REGISTER_FINISH | movementId={} | productId={} | movementType={} | quantity={} | referenceType={} | referenceId={}",
                savedMovement.getId(),
                savedMovement.getProductId(),
                savedMovement.getMovementType(),
                savedMovement.getQuantity(),
                savedMovement.getReferenceType(),
                savedMovement.getReferenceId()
        );
    }

    /**
     * Valida se o produto existe no tenant atual.
     *
     * @param productId id do produto
     */
    private void validateProductExists(UUID productId) {
        boolean exists = tenantProductRepository.existsById(productId);

        log.debug("INVENTORY_VALIDATE_PRODUCT | productId={} | exists={}", productId, exists);

        if (!exists) {
            throw new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "product not found", 404);
        }
    }

    /**
     * Aplica regras de coerência para o ajuste de estoque.
     *
     * <p>Valida:</p>
     * <ul>
     *   <li>quantity não pode ser zero</li>
     *   <li>sinal deve ser coerente com o tipo de movimento</li>
     *   <li>OUTBOUND não pode deixar available negativo</li>
     *   <li>RELEASE_RESERVATION não pode deixar reserved negativo</li>
     * </ul>
     *
     * @param item inventory atual bloqueado
     * @param command comando recebido
     */
    private void validateStockRules(InventoryItem item, AdjustInventoryCommand command) {
        BigDecimal available = safe(item.getQuantityAvailable());
        BigDecimal reserved = safe(item.getQuantityReserved());
        BigDecimal delta = safe(command.getQuantity());

        log.debug(
                "INVENTORY_VALIDATE_RULES_START | productId={} | inventoryId={} | available={} | reserved={} | movementType={} | delta={}",
                item.getProductId(),
                item.getId(),
                available,
                reserved,
                command.getMovementType(),
                delta
        );

        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "quantity cannot be zero",
                    400
            );
        }

        switch (command.getMovementType()) {
            case INBOUND, RETURN, ADJUSTMENT, RESERVATION -> {
                if (delta.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ApiException(
                            ApiErrorCode.INVALID_REQUEST,
                            "quantity must be positive for movement type " + command.getMovementType(),
                            400
                    );
                }
            }

            case OUTBOUND, RELEASE_RESERVATION -> {
                if (delta.compareTo(BigDecimal.ZERO) >= 0) {
                    throw new ApiException(
                            ApiErrorCode.INVALID_REQUEST,
                            "quantity must be negative for movement type " + command.getMovementType(),
                            400
                    );
                }
            }

            default -> {
                // sem ação
            }
        }

        if (command.getMovementType() == InventoryMovementType.OUTBOUND) {
            BigDecimal resulting = available.add(delta);

            log.debug(
                    "INVENTORY_VALIDATE_OUTBOUND | productId={} | availableBefore={} | delta={} | availableAfter={}",
                    item.getProductId(),
                    available,
                    delta,
                    resulting
            );

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

            log.debug(
                    "INVENTORY_VALIDATE_RELEASE_RESERVATION | productId={} | reservedBefore={} | delta={} | reservedAfter={}",
                    item.getProductId(),
                    reserved,
                    delta,
                    resultingReserved
            );

            if (resultingReserved.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(
                        ApiErrorCode.INVALID_REQUEST,
                        "cannot release more reserved stock than available",
                        400
                );
            }
        }

        log.debug(
                "INVENTORY_VALIDATE_RULES_FINISH | productId={} | movementType={} | delta={}",
                item.getProductId(),
                command.getMovementType(),
                delta
        );
    }

    /**
     * Normaliza BigDecimal nulo para zero.
     *
     * @param value valor de entrada
     * @return valor original ou zero
     */
    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Monta amostra textual de movimentos para logs.
     *
     * @param movements lista de movimentos
     * @param limit quantidade máxima de itens na amostra
     * @return string resumida
     */
    private String describeMovements(List<InventoryMovement> movements, int limit) {
        if (movements == null || movements.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        int count = Math.min(limit, movements.size());

        for (int i = 0; i < count; i++) {
            InventoryMovement m = movements.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{id=").append(m.getId())
                    .append(", type=").append(m.getMovementType())
                    .append(", qty=").append(m.getQuantity())
                    .append(", refType=").append(m.getReferenceType())
                    .append(", refId=").append(m.getReferenceId())
                    .append("}");
        }

        if (movements.size() > limit) {
            sb.append(", ... total=").append(movements.size());
        }

        sb.append("]");
        return sb.toString();
    }
}