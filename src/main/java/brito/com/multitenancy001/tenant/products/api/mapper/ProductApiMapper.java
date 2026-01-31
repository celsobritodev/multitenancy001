package brito.com.multitenancy001.tenant.products.api.mapper;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.domain.Product;

@Component
public class ProductApiMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getSku(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getMinStock(),
            product.getMaxStock(),
            product.getCostPrice(),
            product.getProfitMargin(),

            product.getCategory() != null ? product.getCategory().getId() : null,
            product.getCategory() != null ? product.getCategory().getName() : null,
            product.getSubcategory() != null ? product.getSubcategory().getId() : null,
            product.getSubcategory() != null ? product.getSubcategory().getName() : null,

            product.getBrand(),
            product.getWeightKg(),
            product.getDimensions(),
            product.getBarcode(),
            product.getActive(),

            product.getSupplier() != null ? product.getSupplier().getId() : null,
            product.getSupplier() != null ? product.getSupplier().getName() : null,

            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
