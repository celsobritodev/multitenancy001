package brito.com.multitenancy001.tenant.api.dto.products;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        @NotBlank String name,
        String description,
        String sku,
        @NotNull @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer stockQuantity,
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

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ProductResponse {
        if (stockQuantity == null) stockQuantity = 0;
        if (active == null) active = true;
    }

  
}
