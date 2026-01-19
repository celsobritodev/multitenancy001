package brito.com.multitenancy001.tenant.api.dto.products;

import java.util.UUID;

public record SupplierProductCountResponse(UUID supplierId, long count) {}
