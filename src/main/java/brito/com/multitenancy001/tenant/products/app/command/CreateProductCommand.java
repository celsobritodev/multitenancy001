package brito.com.multitenancy001.tenant.products.app.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command de criação de Product (Tenant).
 *
 * Regras de camada:
 * - Command pertence à Application Layer (APP)
 * - Carrega apenas dados primitivos/ids necessários ao use-case
 * - Nunca carrega Entities do domínio
 */
public record CreateProductCommand(
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
        String brand,
        BigDecimal weightKg,
        String dimensions,
        String barcode,
        Boolean active,
        UUID supplierId
) {
}
