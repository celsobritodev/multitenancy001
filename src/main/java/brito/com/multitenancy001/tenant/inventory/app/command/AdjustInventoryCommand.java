package brito.com.multitenancy001.tenant.inventory.app.command;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovementType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command de ajuste/movimentação de estoque.
 */
@Getter
@Builder
public class AdjustInventoryCommand {

    private UUID productId;
    private BigDecimal quantity;
    private InventoryMovementType movementType;
    private String referenceType;
    private String referenceId;
    private String notes;
}