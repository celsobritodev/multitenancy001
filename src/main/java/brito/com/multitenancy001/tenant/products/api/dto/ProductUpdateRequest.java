package brito.com.multitenancy001.tenant.products.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request de UPDATE parcial (PATCH).
 *
 * Regras:
 * - Campos null => não altera.
 * - subcategory:
 *     - clearSubcategory=true e subcategoryId=null => remove subcategoria
 *     - subcategoryId != null => define subcategoria (clearSubcategory é ignorado/forçado false)
 *     - ambos null/false => não altera subcategoria
 */
public record ProductUpdateRequest(
        String name,
        String description,
        String sku,

        @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer stockQuantity,
        @PositiveOrZero Integer minStock,
        @PositiveOrZero Integer maxStock,
        @PositiveOrZero BigDecimal costPrice,

        Long categoryId,

        Long subcategoryId,
        Boolean clearSubcategory, // pode ser null (default false)

        String brand,
        BigDecimal weightKg,
        String dimensions,
        String barcode,
        Boolean active,
        UUID supplierId
) {
}