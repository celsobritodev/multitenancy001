package brito.com.multitenancy001.tenant.sales.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item input for sale create/update.
 */
public record SaleItemRequest(

        UUID productId,

        @NotBlank(message = "productName is required")
        String productName,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.001", message = "quantity must be > 0")
        BigDecimal quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "unitPrice must be > 0")
        BigDecimal unitPrice
) {
    public SaleItemRequest {
        if (productName != null) productName = productName.trim();
    }
}