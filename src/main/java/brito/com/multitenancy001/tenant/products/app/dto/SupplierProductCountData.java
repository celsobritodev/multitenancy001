package brito.com.multitenancy001.tenant.products.app.dto;

import java.util.UUID;

/**
 * DTO de Application Layer para dados agregados (query).
 *
 * Motivo:
 * - Evitar que a camada APP retorne DTOs da API
 * - Controller mapeia este DTO para o Response DTO HTTP
 */
public record SupplierProductCountData(
        UUID supplierId,
        long productCount
) {
}
