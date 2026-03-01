package brito.com.multitenancy001.tenant.products.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request para CREATE e PUT completo.
 *
 * Regras:
 * - categoryId obrigatório
 * - subcategoryId opcional
 * - clearSubcategory:
 *      - true + subcategoryId=null  => remove subcategoria
 *      - subcategoryId != null      => define subcategoria
 *      - ambos null/false           => mantém null (em CREATE) ou mantém atual (em PUT)
 */
public record ProductUpsertRequest(
        @NotBlank String name,
        String description,
        @NotBlank String sku,
        @NotNull @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer stockQuantity,
        Integer minStock,
        Integer maxStock,
        @PositiveOrZero BigDecimal costPrice,

        @NotNull Long categoryId,
        Long subcategoryId,
        Boolean clearSubcategory,

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
        if (clearSubcategory == null) clearSubcategory = false;
    }
}