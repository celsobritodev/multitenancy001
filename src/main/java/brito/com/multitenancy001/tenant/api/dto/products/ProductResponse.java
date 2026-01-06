package brito.com.multitenancy001.tenant.api.dto.products;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import brito.com.multitenancy001.tenant.model.Product;

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

    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getMinStock(),
                product.getMaxStock(),
                product.getCostPrice(),
                product.getProfitMargin(),

                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getSubcategory() != null ? product.getSubcategory().getId() : null,
                product.getSubcategory() != null ? product.getSubcategory().getName() : null,

                product.getBrand(),
                product.getWeightKg(),
                product.getDimensions(),
                product.getBarcode(),
                product.getActive(),

                product.getSupplier() != null ? product.getSupplier().getId() : null,
                product.getSupplier() != null ? product.getSupplier().getName() : null,

                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
