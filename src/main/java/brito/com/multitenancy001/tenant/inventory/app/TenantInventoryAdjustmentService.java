package brito.com.multitenancy001.tenant.inventory.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.inventory.app.command.AdjustInventoryCommand;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.persistence.TenantInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Service de adjustment do módulo de inventory.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar o fluxo transacional de ajuste de estoque.</li>
 *   <li>Aplicar lock do inventory.</li>
 *   <li>Validar coerência de estoque/sinal.</li>
 *   <li>Persistir o saldo final.</li>
 *   <li>Registrar a movimentação no ledger.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantInventoryAdjustmentService {

    private final TenantInventoryRepository inventoryRepository;
    private final TenantInventoryHelper tenantInventoryHelper;
    private final AppClock clock;

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

        tenantInventoryHelper.validateProductExists(command.getProductId());

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
                .orElseGet(() -> tenantInventoryHelper.createInventory(command.getProductId()));

        BigDecimal currentAvailable = tenantInventoryHelper.safe(item.getQuantityAvailable());
        BigDecimal currentReserved = tenantInventoryHelper.safe(item.getQuantityReserved());

        log.debug(
                "INVENTORY_ADJUST_LOCKED | inventoryId={} | productId={} | availableBefore={} | reservedBefore={} | movementType={} | delta={}",
                item.getId(),
                item.getProductId(),
                currentAvailable,
                currentReserved,
                command.getMovementType(),
                command.getQuantity()
        );

        tenantInventoryHelper.validateStockRules(item, command);

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

        tenantInventoryHelper.registerMovement(command);

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
}