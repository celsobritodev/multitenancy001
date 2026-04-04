package brito.com.multitenancy001.tenant.inventory.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

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

/**
 * Helper compartilhado do módulo de inventory.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar inventory inicial quando necessário.</li>
 *   <li>Registrar movimento imutável no ledger.</li>
 *   <li>Validar existência de produto.</li>
 *   <li>Validar regras de coerência de estoque.</li>
 *   <li>Expor helpers utilitários para logs e cálculos seguros.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInventoryHelper {

    private final TenantInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final TenantProductRepository tenantProductRepository;
    private final AppClock clock;

    /**
     * Cria o registro inicial de inventory para um produto.
     *
     * @param productId id do produto
     * @return inventory persistido
     */
    public InventoryItem createInventory(UUID productId) {
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
    public void registerMovement(AdjustInventoryCommand command) {
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
    public void validateProductExists(UUID productId) {
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
    public void validateStockRules(InventoryItem item, AdjustInventoryCommand command) {
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
    public BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Monta amostra textual de movimentos para logs.
     *
     * @param movements lista de movimentos
     * @param limit quantidade máxima de itens na amostra
     * @return string resumida
     */
    public String describeMovements(List<InventoryMovement> movements, int limit) {
        if (movements == null || movements.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        int count = Math.min(limit, movements.size());

        for (int i = 0; i < count; i++) {
            InventoryMovement movement = movements.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{id=").append(movement.getId())
                    .append(", type=").append(movement.getMovementType())
                    .append(", qty=").append(movement.getQuantity())
                    .append(", refType=").append(movement.getReferenceType())
                    .append(", refId=").append(movement.getReferenceId())
                    .append("}");
        }

        if (movements.size() > limit) {
            sb.append(", ... total=").append(movements.size());
        }

        sb.append("]");
        return sb.toString();
    }
}