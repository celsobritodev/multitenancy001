package brito.com.multitenancy001.tenant.sales.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {}