package brito.com.multitenancy001.tenant.inventory.api.dto;

import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovementType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resposta de uma movimentação de estoque.
 */
@Getter
@Builder
public class InventoryMovementResponse {

    private Long id;
    private UUID productId;
    private BigDecimal quantity;
    private InventoryMovementType movementType;
    private String referenceType;
    private String referenceId;
    private String notes;
    private Instant createdAt;
}