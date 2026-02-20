package brito.com.multitenancy001.tenant.products.api;

import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.products.api.dto.SupplierProductCountResponse;
import brito.com.multitenancy001.tenant.products.api.mapper.ProductApiMapper;
import brito.com.multitenancy001.tenant.products.app.TenantProductService;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
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

/**
 * Tenant API: Products.
 *
 * Padrão definitivo:
 * - Controller só HTTP (DTO Request/Response)
 * - Controller NÃO cria/manipula Entity
 * - Controller chama Application Service via Command (APP)
 * - Mapper converte Domain -> Response DTO
 */
@RestController
@RequestMapping("/api/tenant/products")
@RequiredArgsConstructor
public class TenantProductController {

    private final ProductApiMapper productApiMapper;
    private final TenantProductService tenantProductService;

    /**
     * Busca produto por id (escopo: tenant).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        // método: delega ao service e mapeia para response DTO
        Product product = tenantProductService.findById(id);
        return ResponseEntity.ok(productApiMapper.toResponse(product));
    }

    /**
     * Lista paginada de produtos (default: conforme regras do service).
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<Page<ProductResponse>> list(Pageable pageable) {
        // método: paginação e mapeamento
        Page<ProductResponse> page = tenantProductService.findAll(pageable).map(productApiMapper::toResponse);
        return ResponseEntity.ok(page);
    }

    /**
     * Lista produtos por categoria (default: somente não-deletados/ativos conforme service).
     */
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listByCategory(@PathVariable Long categoryId) {
        // método: consulta e mapeamento
        List<ProductResponse> out = tenantProductService.findByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Lista produtos por subcategoria (default: conforme regras do service).
     */
    @GetMapping("/subcategory/{subcategoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listBySubcategory(@PathVariable Long subcategoryId) {
        // método: consulta e mapeamento
        List<ProductResponse> out = tenantProductService.findBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Lista produtos por supplier (default: conforme regras do service).
     */
    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listBySupplier(@PathVariable UUID supplierId) {
        // método: consulta e mapeamento
        List<ProductResponse> out = tenantProductService.findBySupplierId(supplierId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Query: contagem de produtos por supplier (agregado).
     */
    @GetMapping("/count-by-supplier")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<SupplierProductCountResponse>> countBySupplier() {
        // método: service retorna app.dto e controller mapeia para response DTO
        var rows = tenantProductService.countProductsBySupplier();
        var out = rows.stream()
                .map(r -> new SupplierProductCountResponse(r.supplierId(), r.productCount()))
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Retorna o valor total do inventário (estoque * custo) do tenant.
     */
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        // método: delega cálculo ao service
        BigDecimal value = tenantProductService.calculateTotalInventoryValue();
        return ResponseEntity.ok(value != null ? value : BigDecimal.ZERO);
    }

    /**
     * Retorna a contagem de produtos com estoque baixo.
     */
    @GetMapping("/low-stock/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<Long> countLowStock(@RequestParam(name = "threshold", defaultValue = "5") Integer threshold) {
        // método: delega cálculo ao service
        Long count = tenantProductService.countLowStockProducts(threshold);
        return ResponseEntity.ok(count != null ? count : 0L);
    }

    /**
     * Alterna status ativo/inativo do produto.
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable UUID id) {
        // método: delega ao service e mapeia response
        Product updated = tenantProductService.toggleActive(id);
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Atualiza o custo do produto (costPrice).
     */
    @PatchMapping("/{id}/cost-price")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> updateCostPrice(@PathVariable UUID id, @RequestParam BigDecimal costPrice) {
        // método: delega ao service e mapeia response
        Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);
        return ResponseEntity.ok(productApiMapper.toResponse(updatedProduct));
    }

    /**
     * Cria produto detalhado a partir de um request DTO (upsert).
     *
     * Importante:
     * - Controller NÃO cria entities (Product/Category/Subcategory/Supplier)
     * - Controller transforma DTO -> Command e chama o service
     */
    @PostMapping("/detailed")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> createDetailedProduct(@Valid @RequestBody ProductUpsertRequest req) {
        // método: transforma request em command e delega ao service
        CreateProductCommand cmd = new CreateProductCommand(
                req.name(),
                req.description(),
                req.sku(),
                req.price(),
                req.stockQuantity(),
                req.minStock(),
                req.maxStock(),
                req.costPrice(),
                req.categoryId(),
                req.subcategoryId(),
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );

        Product savedProduct = tenantProductService.create(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(productApiMapper.toResponse(savedProduct));
    }

    /**
     * "Any" por categoria: pode incluir deleted/inactive conforme regra do service.
     */
    @GetMapping("/any/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> findAnyByCategory(@PathVariable Long categoryId) {
        // método: consulta "any" e mapeia
        List<ProductResponse> out = tenantProductService.findAnyByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * "Any" por subcategoria: pode incluir deleted/inactive conforme regra do service.
     */
    @GetMapping("/any/subcategory/{subcategoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> findAnyBySubcategory(@PathVariable Long subcategoryId) {
        // método: consulta "any" e mapeia
        List<ProductResponse> out = tenantProductService.findAnyBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * "Any" por marca: pode incluir deleted/inactive conforme regra do service.
     */
    @GetMapping("/any/brand")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> findAnyByBrand(@RequestParam("brand") String brand) {
        // método: consulta "any" e mapeia
        List<ProductResponse> out = tenantProductService.findAnyByBrandIgnoreCase(brand).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Busca por nome (lista simples).
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> searchByName(
            @RequestParam("name") String name,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "minStock", required = false) Integer minStock,
            @RequestParam(name = "maxStock", required = false) Integer maxStock
    ) {
        // método: delega filtro ao service e mapeia
        List<ProductResponse> out = tenantProductService.searchProducts(name, minPrice, maxPrice, minStock, maxStock).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }
}
