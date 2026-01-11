package brito.com.multitenancy001.tenant.api.controller.products;

import brito.com.multitenancy001.tenant.api.dto.products.ProductResponse;
import brito.com.multitenancy001.tenant.api.dto.products.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.api.mapper.ProductApiMapper;
import brito.com.multitenancy001.tenant.application.product.TenantProductService;
import brito.com.multitenancy001.tenant.domain.category.Category;
import brito.com.multitenancy001.tenant.domain.category.Subcategory;
import brito.com.multitenancy001.tenant.domain.product.Product;
import brito.com.multitenancy001.tenant.domain.supplier.Supplier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant/products")
@RequiredArgsConstructor
public class TenantProductController {

    private final ProductApiMapper productApiMapper;
    private final TenantProductService tenantProductService;

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('TEN_PRODUCT_READ')")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = tenantProductService.findByCategoryId(categoryId);
        List<ProductResponse> dtos = products.stream().map(productApiMapper::toResponse).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/brand/{brand}")
    @PreAuthorize("hasAuthority('TEN_PRODUCT_READ')")
    public ResponseEntity<List<ProductResponse>> getProductsByBrand(@PathVariable String brand) {
        List<Product> products = tenantProductService.findByBrand(brand);
        List<ProductResponse> dtos = products.stream().map(productApiMapper::toResponse).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_PRODUCT_READ')")
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        List<Product> products = tenantProductService.findActiveProducts();
        List<ProductResponse> dtos = products.stream().map(productApiMapper::toResponse).toList();
        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/{id}/cost-price")
    @PreAuthorize("hasAuthority('TEN_PRODUCT_WRITE')")
    public ResponseEntity<ProductResponse> updateCostPrice(
            @PathVariable UUID id,
            @RequestParam BigDecimal costPrice
    ) {
        Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);
        return ResponseEntity.ok(productApiMapper.toResponse(updatedProduct));
    }

    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority('TEN_INVENTORY_READ')")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        BigDecimal value = tenantProductService.calculateTotalInventoryValue();
        return ResponseEntity.ok(value != null ? value : BigDecimal.ZERO);
    }

    @GetMapping("/low-stock/count")
    @PreAuthorize("hasAuthority('TEN_INVENTORY_READ')")
    public ResponseEntity<Long> countLowStockProducts(@RequestParam(defaultValue = "10") Integer threshold) {
        Long count = tenantProductService.countLowStockProducts(threshold);
        return ResponseEntity.ok(count != null ? count : 0L);
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('TEN_PRODUCT_WRITE')")
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable UUID id) {
        Product product = tenantProductService.findById(id);
        product.setActive(!Boolean.TRUE.equals(product.getActive()));
        tenantProductService.create(product);
        return ResponseEntity.ok(productApiMapper.toResponse(product));
    }

    @PostMapping("/detailed")
    @PreAuthorize("hasAuthority('TEN_PRODUCT_WRITE')")
    public ResponseEntity<ProductResponse> createDetailedProduct(@Valid @RequestBody ProductUpsertRequest request) {

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setMinStock(request.minStock());
        product.setMaxStock(request.maxStock());
        product.setCostPrice(request.costPrice());
        product.setBrand(request.brand());
        product.setWeightKg(request.weightKg());
        product.setDimensions(request.dimensions());
        product.setBarcode(request.barcode());
        product.setActive(request.active());

        Category category = new Category();
        category.setId(request.categoryId());
        product.setCategory(category);

        if (request.subcategoryId() != null) {
            Subcategory sub = new Subcategory();
            sub.setId(request.subcategoryId());
            product.setSubcategory(sub);
        }

        if (request.supplierId() != null) {
            Supplier supplier = new Supplier();
            supplier.setId(request.supplierId());
            product.setSupplier(supplier);
        }

        Product savedProduct = tenantProductService.create(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(productApiMapper.toResponse(savedProduct));
    }
}
