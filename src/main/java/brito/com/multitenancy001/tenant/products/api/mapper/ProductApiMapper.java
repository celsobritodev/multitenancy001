package brito.com.multitenancy001.tenant.products.api.mapper;

import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.domain.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductApiMapper {

    public ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getSku(),
                p.getPrice(),
                p.getStockQuantity(),
                p.getMinStock(),
                p.getMaxStock(),
                p.getCostPrice(),
                p.getProfitMargin(),

                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getSubcategory() != null ? p.getSubcategory().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getId() : null,

                p.getBrand(),
                p.getWeightKg(),
                p.getDimensions(),
                p.getBarcode(),

                p.getActive(),
                p.getDeleted(),

                p.getAudit() != null ? p.getAudit().getCreatedAt() : null,
                p.getAudit() != null ? p.getAudit().getUpdatedAt() : null,
                p.getAudit() != null ? p.getAudit().getDeletedAt() : null
        );
    }
}