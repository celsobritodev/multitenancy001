package brito.com.multitenancy001.tenant.products.api;

import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.products.api.dto.SupplierProductCountResponse;
import brito.com.multitenancy001.tenant.products.api.mapper.ProductApiMapper;
import brito.com.multitenancy001.tenant.products.app.TenantProductService;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Busca produto por id (escopo: tenant).
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        Product product = tenantProductService.findById(id);
        return ResponseEntity.ok(productApiMapper.toResponse(product));
    }

    // Lista produtos por categoria (default: somente não-deletados/ativos conforme service).
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = tenantProductService.findByCategoryId(categoryId);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Lista produtos por categoria com flags administrativas (incluir deletados/inativos).
    @GetMapping("/category/{categoryId}/admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsByCategoryAdmin(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        List<Product> products = tenantProductService.findByCategoryId(categoryId, includeDeleted, includeInactive);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Lista produtos por categoria e subcategoria opcional.
    @GetMapping("/category/{categoryId}/optional")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsByCategoryOptionalSubcategory(
            @PathVariable Long categoryId,
            @RequestParam(value = "subcategoryId", required = false) Long subcategoryId
    ) {
        List<Product> products = tenantProductService.findByCategoryAndOptionalSubcategory(categoryId, subcategoryId);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Lista produtos por subcategoria.
    @GetMapping("/subcategory/{subcategoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsBySubcategory(@PathVariable Long subcategoryId) {
        List<Product> products = tenantProductService.findBySubcategoryId(subcategoryId);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Lista produtos por marca.
    @GetMapping("/brand/{brand}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsByBrand(@PathVariable String brand) {
        List<Product> products = tenantProductService.findByBrand(brand);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Lista produtos ativos.
    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        List<Product> products = tenantProductService.findActiveProducts();
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Pesquisa produtos por nome.
    @GetMapping("/name")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsByName(@RequestParam("name") String name) {
        List<Product> products = tenantProductService.findByName(name);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Pesquisa produtos por nome com paginação.
    @GetMapping("/name/paged")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<Page<ProductResponse>> getProductsByNamePaged(
            @RequestParam("name") String name,
            Pageable pageable
    ) {
        Page<Product> page = tenantProductService.findByNamePaged(name, pageable);
        return ResponseEntity.ok(page.map(productApiMapper::toResponse));
    }

    // Lista produtos por faixa de preço.
    @GetMapping("/price-between")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsByPriceBetween(
            @RequestParam("minPrice") BigDecimal minPrice,
            @RequestParam("maxPrice") BigDecimal maxPrice
    ) {
        List<Product> products = tenantProductService.findByPriceBetween(minPrice, maxPrice);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Lista produtos por fornecedor.
    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> getProductsBySupplier(@PathVariable UUID supplierId) {
        List<Product> products = tenantProductService.findBySupplierId(supplierId);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Filtra produtos por múltiplos critérios (nome/preço/estoque).
    @GetMapping("/filter")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> filterProducts(
            @RequestParam("name") String name,
            @RequestParam("minPrice") BigDecimal minPrice,
            @RequestParam("maxPrice") BigDecimal maxPrice,
            @RequestParam("minStock") Integer minStock,
            @RequestParam("maxStock") Integer maxStock
    ) {
        List<Product> products =
                tenantProductService.findByNameAndPriceAndStock(name, minPrice, maxPrice, minStock, maxStock);
        return ResponseEntity.ok(products.stream().map(productApiMapper::toResponse).toList());
    }

    // Retorna contagem de produtos agrupada por fornecedor.
    @GetMapping("/stats/count-by-supplier")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<SupplierProductCountResponse>> countBySupplier() {
        return ResponseEntity.ok(tenantProductService.countProductsBySupplier());
    }

    // Retorna o valor total do inventário (estoque * custo) do tenant.
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        BigDecimal value = tenantProductService.calculateTotalInventoryValue();
        return ResponseEntity.ok(value != null ? value : BigDecimal.ZERO);
    }

    // Retorna a contagem de produtos com estoque baixo.
    @GetMapping("/low-stock/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<Long> countLowStockProducts(@RequestParam(defaultValue = "10") Integer threshold) {
        Long count = tenantProductService.countLowStockProducts(threshold);
        return ResponseEntity.ok(count != null ? count : 0L);
    }

    // Alterna status ativo/inativo do produto.
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable UUID id) {
        Product updated = tenantProductService.toggleActive(id);
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    // Atualiza o custo do produto (costPrice).
    @PatchMapping("/{id}/cost-price")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> updateCostPrice(
            @PathVariable UUID id,
            @RequestParam BigDecimal costPrice
    ) {
        Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);
        return ResponseEntity.ok(productApiMapper.toResponse(updatedProduct));
    }

    // Cria produto detalhado a partir de um request DTO (upsert).
    @PostMapping("/detailed")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> createDetailedProduct(@Valid @RequestBody ProductUpsertRequest productUpsertRequest) {

        Product product = new Product();
        product.setName(productUpsertRequest.name());
        product.setDescription(productUpsertRequest.description());
        product.setSku(productUpsertRequest.sku());
        product.setPrice(productUpsertRequest.price());
        product.setStockQuantity(productUpsertRequest.stockQuantity());
        product.setMinStock(productUpsertRequest.minStock());
        product.setMaxStock(productUpsertRequest.maxStock());
        product.setCostPrice(productUpsertRequest.costPrice());
        product.setBrand(productUpsertRequest.brand());
        product.setWeightKg(productUpsertRequest.weightKg());
        product.setDimensions(productUpsertRequest.dimensions());
        product.setBarcode(productUpsertRequest.barcode());
        product.setActive(productUpsertRequest.active());

        Category category = new Category();
        category.setId(productUpsertRequest.categoryId());
        product.setCategory(category);

        if (productUpsertRequest.subcategoryId() != null) {
            Subcategory sub = new Subcategory();
            sub.setId(productUpsertRequest.subcategoryId());
            product.setSubcategory(sub);
        }

        if (productUpsertRequest.supplierId() != null) {
            Supplier supplier = new Supplier();
            supplier.setId(productUpsertRequest.supplierId());
            product.setSupplier(supplier);
        }

        Product savedProduct = tenantProductService.create(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(productApiMapper.toResponse(savedProduct));
    }

    // "Any" (pode incluir deleted/inactive) - útil para telas/admin/relatórios internos
    @GetMapping("/category/{categoryId}/any")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listAnyByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(
                tenantProductService.findAnyByCategoryId(categoryId)
                        .stream()
                        .map(productApiMapper::toResponse)
                        .toList()
        );
    }

    @GetMapping("/subcategory/{subcategoryId}/any")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listAnyBySubcategory(@PathVariable Long subcategoryId) {
        return ResponseEntity.ok(
                tenantProductService.findAnyBySubcategoryId(subcategoryId)
                        .stream()
                        .map(productApiMapper::toResponse)
                        .toList()
        );
    }

    @GetMapping("/brand/{brand}/any")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listAnyByBrand(@PathVariable String brand) {
        return ResponseEntity.ok(
                tenantProductService.findAnyByBrandIgnoreCase(brand)
                        .stream()
                        .map(productApiMapper::toResponse)
                        .toList()
        );
    }
}
