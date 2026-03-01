package brito.com.multitenancy001.tenant.products.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductCreateRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        String description,

        @NotBlank
        @Size(max = 100)
        String sku,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal price,

        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal costPrice,

        @Min(0)
        Integer stockQuantity,

        @Min(0)
        Integer minStock,

        @Min(0)
        Integer maxStock,

        @Size(max = 100)
        String brand,

        @DecimalMin(value = "0.000", inclusive = true)
        BigDecimal weightKg,

        @Size(max = 50)
        String dimensions,

        @Size(max = 50)
        String barcode,

        Boolean active,

        UUID supplierId,

        @NotNull
        Long categoryId,

        Long subcategoryId
) {}