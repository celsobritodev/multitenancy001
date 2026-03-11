package brito.com.multitenancy001.tenant.inventory.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resposta com o estado atual do estoque do produto.
 */
@Getter
@Builder
public class InventoryResponse {

    private UUID productId;
    private BigDecimal quantityAvailable;
    private BigDecimal quantityReserved;
    private BigDecimal minStock;
    private boolean lowStock;
}