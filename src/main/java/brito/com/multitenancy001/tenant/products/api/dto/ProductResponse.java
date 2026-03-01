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
        Long subcategoryId,
        UUID supplierId,

        String brand,
        BigDecimal weightKg,
        String dimensions,
        String barcode,

        Boolean active,
        Boolean deleted,

        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}