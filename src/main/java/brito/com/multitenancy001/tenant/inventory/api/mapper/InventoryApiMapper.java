package brito.com.multitenancy001.tenant.inventory.api.mapper;

import brito.com.multitenancy001.tenant.inventory.api.dto.InventoryMovementResponse;
import brito.com.multitenancy001.tenant.inventory.api.dto.InventoryResponse;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;

import java.util.List;

/**
 * Mapper da camada API para o módulo Inventory.
 */
public final class InventoryApiMapper {

    private InventoryApiMapper() {
    }

    /**
     * Converte InventoryItem em InventoryResponse.
     */
    public static InventoryResponse toResponse(InventoryItem item) {
        return InventoryResponse.builder()
                .productId(item.getProductId())
                .quantityAvailable(item.getQuantityAvailable())
                .quantityReserved(item.getQuantityReserved())
                .minStock(item.getMinStock())
                .lowStock(item.isLowStock())
                .build();
    }

    /**
     * Converte InventoryMovement em InventoryMovementResponse.
     */
    public static InventoryMovementResponse toMovementResponse(InventoryMovement movement) {
        return InventoryMovementResponse.builder()
                .id(movement.getId())
                .productId(movement.getProductId())
                .quantity(movement.getQuantity())
                .movementType(movement.getMovementType())
                .referenceType(movement.getReferenceType())
                .referenceId(movement.getReferenceId())
                .notes(movement.getNotes())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    /**
     * Converte lista de movimentos para lista de responses.
     */
    public static List<InventoryMovementResponse> toMovementResponses(List<InventoryMovement> movements) {
        return movements.stream()
                .map(InventoryApiMapper::toMovementResponse)
                .toList();
    }
}