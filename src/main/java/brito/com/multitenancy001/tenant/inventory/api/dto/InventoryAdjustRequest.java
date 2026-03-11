package brito.com.multitenancy001.tenant.inventory.api.dto;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request de movimentação/ajuste manual de estoque.
 */
@Getter
@Setter
public class InventoryAdjustRequest {

    @NotNull
    private UUID productId;

    @NotNull
    @DecimalMin(value = "-999999999999999.9999", inclusive = true)
    private BigDecimal quantity;

    @NotNull
    private InventoryMovementType movementType;

    private String referenceType;

    private String referenceId;

    private String notes;
}