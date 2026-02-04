package brito.com.multitenancy001.tenant.products.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        String sku,
        BigDecimal price,
        Integer stockQuantity,
        Integer minStock,
        Integer maxStock,
        BigDecimal costPrice,
        BigDecimal profitMargin,

        Long categoryId,
        String categoryName,
        Long subcategoryId,
        String subcategoryName,

        String brand,
        BigDecimal weightKg,
        String dimensions,
        String barcode,
        Boolean active,

        UUID supplierId,
        String supplierName,

        // ✅ Instantes reais na borda HTTP (JSON com "Z")
        Instant createdAt,
        Instant updatedAt
) {
    public ProductResponse {
        if (stockQuantity == null) stockQuantity = 0;
        if (active == null) active = true;
    }
}

