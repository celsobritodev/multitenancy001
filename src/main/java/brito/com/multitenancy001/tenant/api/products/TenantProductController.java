package brito.com.multitenancy001.tenant.api.products;

import brito.com.multitenancy001.tenant.api.dto.products.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.api.dto.products.ProductResponse;
import brito.com.multitenancy001.tenant.application.TenantProductService;
import brito.com.multitenancy001.tenant.model.Category;
import brito.com.multitenancy001.tenant.model.Product;
import brito.com.multitenancy001.tenant.model.Subcategory;
import brito.com.multitenancy001.tenant.model.Supplier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class TenantProductController {

	private final TenantProductService tenantProductService;

	// Novos endpoints para os campos adicionais

	@GetMapping("/category/{categoryId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'VIEWER')")
	public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
	    List<Product> products = tenantProductService.findByCategoryId(categoryId);
	    List<ProductResponse> dtos = products.stream().map(ProductResponse::fromEntity).toList();
	    return ResponseEntity.ok(dtos);
	}


	@GetMapping("/brand/{brand}")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'VIEWER')")
	public ResponseEntity<List<ProductResponse>> getProductsByBrand(@PathVariable String brand) {
		List<Product> products = tenantProductService.findByBrand(brand);
		List<ProductResponse> productDTOs = products.stream().map(ProductResponse::fromEntity).collect(Collectors.toList());
		return ResponseEntity.ok(productDTOs);
	}

	@GetMapping("/active")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'VIEWER')")
	public ResponseEntity<List<ProductResponse>> getActiveProducts() {
		List<Product> products = tenantProductService.findActiveProducts();
		List<ProductResponse> productDTOs = products.stream().map(ProductResponse::fromEntity).collect(Collectors.toList());
		return ResponseEntity.ok(productDTOs);
	}

	@PatchMapping("/{id}/cost-price")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
	public ResponseEntity<ProductResponse> updateCostPrice(@PathVariable UUID id, @RequestParam BigDecimal costPrice) {

		Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);
		return ResponseEntity.ok(ProductResponse.fromEntity(updatedProduct));
	}

	@GetMapping("/inventory-value")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
	public ResponseEntity<BigDecimal> getTotalInventoryValue() {
		BigDecimal value = tenantProductService.calculateTotalInventoryValue();
		return ResponseEntity.ok(value != null ? value : BigDecimal.ZERO);
	}

	@GetMapping("/low-stock/count")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
	public ResponseEntity<Long> countLowStockProducts(@RequestParam(defaultValue = "10") Integer threshold) {

		Long count = tenantProductService.countLowStockProducts(threshold);
		return ResponseEntity.ok(count != null ? count : 0L);
	}

	@PatchMapping("/{id}/toggle-active")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
	public ResponseEntity<ProductResponse> toggleActive(@PathVariable UUID id) {
		Product product = tenantProductService.findById(id);
		product.setActive(!Boolean.TRUE.equals(product.getActive()));
		tenantProductService.create(product); // Reutiliza o método save
		return ResponseEntity.ok(ProductResponse.fromEntity(product));
	}

	@PostMapping("/detailed")
	@PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
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

		// ✅ Category obrigatória
		Category category = new Category();
		category.setId(request.categoryId());
		product.setCategory(category);

		// ✅ Subcategory opcional
		if (request.subcategoryId() != null) {
			Subcategory sub = new Subcategory();
			sub.setId(request.subcategoryId());
			product.setSubcategory(sub);
		}

		// Supplier (como você já fazia)
		if (request.supplierId() != null) {
			Supplier supplier = new Supplier();
			 supplier.setId(request.supplierId());
			product.setSupplier(supplier);
		}

		Product savedProduct = tenantProductService.create(product);
		return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.fromEntity(savedProduct));
	}

}