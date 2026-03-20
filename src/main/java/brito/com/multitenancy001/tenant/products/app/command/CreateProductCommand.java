package brito.com.multitenancy001.tenant.products.app.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command de criação de Product (Tenant).
 *
 * <p>Regras de camada:</p>
 * <ul>
 *   <li>Command pertence à Application Layer (APP).</li>
 *   <li>Carrega apenas dados primitivos e ids necessários ao use-case.</li>
 *   <li>Nunca carrega entities do domínio.</li>
 * </ul>
 *
 * <p>Observação importante:</p>
 * <ul>
 *   <li>O campo {@code accountId} é obrigatório para enforcement de quota no write-path.</li>
 *   <li>Ele permite que o serviço aplique validação de plano antes da criação do produto.</li>
 * </ul>
 */
public record CreateProductCommand(
        Long accountId,
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