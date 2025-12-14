package brito.com.multitenancy001.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

//Record para criação/atualização expandido
public record ProductRequest(
     @NotBlank String name,
     String description,
     String sku,
     @NotNull @PositiveOrZero BigDecimal price,
     @PositiveOrZero Integer stockQuantity,
     Integer minStock,
     Integer maxStock,
     BigDecimal costPrice,
     String category,
     String brand,
     BigDecimal weightKg,
     String dimensions,
     String barcode,
     Boolean active,
     String supplierId
) {
 public ProductRequest {
     if (stockQuantity == null) {
         stockQuantity = 0;
     }
     if (active == null) {
         active = true;
     }
 }
}