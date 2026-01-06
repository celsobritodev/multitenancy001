package brito.com.multitenancy001.tenant.api.dto.products;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Record para criação/atualização expandido (com Category/Subcategory por ID)
public record ProductUpsertRequest(
     @NotBlank String name,
     String description,
     String sku,
     @NotNull @PositiveOrZero BigDecimal price,
     @PositiveOrZero Integer stockQuantity,
     Integer minStock,
     Integer maxStock,
     BigDecimal costPrice,

     @NotNull Long categoryId,     // ✅ obrigatório
     Long subcategoryId,           // ✅ opcional

     String brand,
     BigDecimal weightKg,
     String dimensions,
     String barcode,
     Boolean active,
     UUID supplierId
) {
    public ProductUpsertRequest {
        if (stockQuantity == null) stockQuantity = 0;
        if (active == null) active = true;
    }
}
