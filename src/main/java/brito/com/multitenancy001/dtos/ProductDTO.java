package brito.com.multitenancy001.dtos;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import brito.com.multitenancy001.entities.tenant.Product;

public record ProductDTO(
        String id,
        @NotBlank String name,
        String description,
        String sku,
        @NotNull @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer stockQuantity,
        Integer minStock,
        Integer maxStock,
        BigDecimal costPrice,
        BigDecimal profitMargin,
        String category,
        String brand,
        BigDecimal weightKg,
        String dimensions,
        String barcode,
        Boolean active,
        String supplierId,
        String supplierName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ProductDTO {
        if (stockQuantity == null) {
            stockQuantity = 0;
        }
        if (active == null) {
            active = true;
        }
    }
    
    public static ProductDTO fromEntity(Product product) {
        return new ProductDTO(
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
                product.getCategory(),
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

