package brito.com.multitenancy001.controllers;


import brito.com.multitenancy001.dtos.ProductDTO;
import brito.com.multitenancy001.dtos.ProductRequest;
import brito.com.multitenancy001.entities.tenant.Product;
import brito.com.multitenancy001.entities.tenant.Supplier;
import brito.com.multitenancy001.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    

    
    // Novos endpoints para os campos adicionais
    
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'VIEWER')")
    public ResponseEntity<List<ProductDTO>> getProductsByCategory(@PathVariable String category) {
        List<Product> products = productService.findByCategory(category);
        List<ProductDTO> productDTOs = products.stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productDTOs);
    }
    
    @GetMapping("/brand/{brand}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'VIEWER')")
    public ResponseEntity<List<ProductDTO>> getProductsByBrand(@PathVariable String brand) {
        List<Product> products = productService.findByBrand(brand);
        List<ProductDTO> productDTOs = products.stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productDTOs);
    }
    
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'VIEWER')")
    public ResponseEntity<List<ProductDTO>> getActiveProducts() {
        List<Product> products = productService.findActiveProducts();
        List<ProductDTO> productDTOs = products.stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productDTOs);
    }
    
    @PatchMapping("/{id}/cost-price")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<ProductDTO> updateCostPrice(
            @PathVariable String id,
            @RequestParam BigDecimal costPrice) {
        
        Product updatedProduct = productService.updateCostPrice(id, costPrice);
        return ResponseEntity.ok(ProductDTO.fromEntity(updatedProduct));
    }
    
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        BigDecimal value = productService.calculateTotalInventoryValue();
        return ResponseEntity.ok(value != null ? value : BigDecimal.ZERO);
    }
    
    @GetMapping("/low-stock/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Long> countLowStockProducts(
            @RequestParam(defaultValue = "10") Integer threshold) {
        
        Long count = productService.countLowStockProducts(threshold);
        return ResponseEntity.ok(count != null ? count : 0L);
    }
    
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<ProductDTO> toggleActive(@PathVariable String id) {
        Product product = productService.findById(id);
        product.setActive(!Boolean.TRUE.equals(product.getActive()));
        productService.create(product); // Reutiliza o método save
        return ResponseEntity.ok(ProductDTO.fromEntity(product));
    }
    
    // Endpoint para criação com campos expandidos
    @PostMapping("/detailed")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<ProductDTO> createDetailedProduct(@Valid @RequestBody ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setMinStock(request.minStock());
        product.setMaxStock(request.maxStock());
        product.setCostPrice(request.costPrice());
        product.setCategory(request.category());
        product.setBrand(request.brand());
        product.setWeightKg(request.weightKg());
        product.setDimensions(request.dimensions());
        product.setBarcode(request.barcode());
        product.setActive(request.active());
        
        // Configura supplier se fornecido
        if (request.supplierId() != null) {
            Supplier supplier = new Supplier();
            supplier.setId(request.supplierId());
            product.setSupplier(supplier);
        }
        
        Product savedProduct = productService.create(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDTO.fromEntity(savedProduct));
    }
}