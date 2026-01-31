package brito.com.multitenancy001.tenant.products.api.dto;

import java.util.UUID;

public record SupplierProductCountResponse(UUID supplierId, long count) {}
