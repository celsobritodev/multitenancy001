package brito.com.multitenancy001.tenant.products.app.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command de update de Product (Tenant).
 *
 * Semântica (para evitar "tri-state" com Entities):
 * - Campos nulos => não alteram (patch-like)
 * - categoryId se não-nulo => troca categoria
 * - subcategory:
 *     - clearSubcategory=true => remove subcategory
 *     - subcategoryId != null => define subcategory
 *     - ambos nulos/false => não altera subcategory
 * - supplierId se não-nulo => troca supplier (não há clear explícito aqui)
 */
public record UpdateProductCommand(
        String name,
        String description,
        String sku,
        BigDecimal price,
        Integer stockQuantity,
        Integer minStock,
        Integer maxStock,
        BigDecimal costPrice,
        Long categoryId,
        Long subcategoryId,
        boolean clearSubcategory,
        String brand,
        BigDecimal weightKg,
        String dimensions,
        String barcode,
        Boolean active,
        UUID supplierId
) {
}
