package brito.com.multitenancy001.tenant.products.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpdateRequest;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.products.api.dto.SupplierProductCountResponse;
import brito.com.multitenancy001.tenant.products.api.mapper.ProductApiMapper;
import brito.com.multitenancy001.tenant.products.app.TenantProductService;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tenant API: Products.
 *
 * <p>Padrão definitivo:</p>
 * <ul>
 *   <li>Controller só HTTP (DTO Request/Response).</li>
 *   <li>Controller não cria/manipula Entity de domínio.</li>
 *   <li>Controller transforma DTO em Command da camada APP.</li>
 *   <li>Controller delega regras de negócio ao Application Service.</li>
 * </ul>
 *
 * <p>Diretriz importante:</p>
 * <ul>
 *   <li>Para criação de produto, o controller resolve o {@code accountId} atual da identidade tenant.</li>
 *   <li>Esse {@code accountId} é enviado ao write-path para enforcement de quota.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/products")
@RequiredArgsConstructor
@Slf4j
public class TenantProductController {

    private final ProductApiMapper productApiMapper;
    private final TenantProductService tenantProductService;
    private final TenantRequestIdentityService tenantRequestIdentityService;

    /**
     * Busca produto por id no escopo tenant.
     *
     * @param id id do produto
     * @return produto encontrado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        Product product = tenantProductService.findById(id);
        return ResponseEntity.ok(productApiMapper.toResponse(product));
    }

    /**
     * Lista paginada de produtos.
     *
     * @param pageable paginação
     * @return página de produtos
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<Page<ProductResponse>> list(Pageable pageable) {
        Page<ProductResponse> page = tenantProductService.findAll(pageable)
                .map(productApiMapper::toResponse);
        return ResponseEntity.ok(page);
    }

    /**
     * Lista produtos por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos da categoria
     */
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listByCategory(@PathVariable Long categoryId) {
        List<ProductResponse> out = tenantProductService.findByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Lista produtos por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos da subcategoria
     */
    @GetMapping("/subcategory/{subcategoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listBySubcategory(@PathVariable Long subcategoryId) {
        List<ProductResponse> out = tenantProductService.findBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Lista produtos por fornecedor.
     *
     * @param supplierId id do fornecedor
     * @return produtos do fornecedor
     */
    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listBySupplier(@PathVariable UUID supplierId) {
        List<ProductResponse> out = tenantProductService.findBySupplierId(supplierId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Retorna contagem de produtos por fornecedor.
     *
     * @return lista agregada por fornecedor
     */
    @GetMapping("/count-by-supplier")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<SupplierProductCountResponse>> countBySupplier() {
        var rows = tenantProductService.countProductsBySupplier();
        var out = rows.stream()
                .map(r -> new SupplierProductCountResponse(r.supplierId(), r.productCount()))
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Retorna o valor total do inventário do tenant.
     *
     * @return valor total
     */
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        BigDecimal value = tenantProductService.calculateTotalInventoryValue();
        return ResponseEntity.ok(value != null ? value : BigDecimal.ZERO);
    }

    /**
     * Retorna a quantidade de produtos com estoque baixo.
     *
     * @param threshold limiar de estoque baixo
     * @return quantidade encontrada
     */
    @GetMapping("/low-stock/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<Long> countLowStock(@RequestParam(name = "threshold", defaultValue = "5") Integer threshold) {
        Long count = tenantProductService.countLowStockProducts(threshold);
        return ResponseEntity.ok(count != null ? count : 0L);
    }

    /**
     * Alterna status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable UUID id) {
        Product updated = tenantProductService.toggleActive(id);
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    @PatchMapping("/{id}/cost-price")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> updateCostPrice(@PathVariable UUID id, @RequestParam BigDecimal costPrice) {
        Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);
        return ResponseEntity.ok(productApiMapper.toResponse(updatedProduct));
    }

    /**
     * Cria produto detalhado a partir de DTO de request.
     *
     * <p>Importante:</p>
     * <ul>
     *   <li>Controller não cria entity de domínio.</li>
     *   <li>Controller resolve o {@code accountId} da identidade tenant atual.</li>
     *   <li>Controller monta command e delega ao service.</li>
     * </ul>
     *
     * @param req payload HTTP
     * @return produto criado
     */
    @PostMapping("/detailed")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> createDetailedProduct(@Valid @RequestBody ProductUpsertRequest req) {
        Long accountId = requireCurrentAccountId();

        log.info(
                "Recebida requisição de criação detalhada de produto. accountId={}, sku={}, name={}",
                accountId,
                req.sku(),
                req.name()
        );

        CreateProductCommand cmd = new CreateProductCommand(
                accountId,
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

        log.info(
                "Produto criado via API com sucesso. accountId={}, productId={}",
                accountId,
                savedProduct.getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(productApiMapper.toResponse(savedProduct));
    }

    /**
     * Busca produtos de qualquer status por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos encontrados
     */
    @GetMapping("/any/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> findAnyByCategory(@PathVariable Long categoryId) {
        List<ProductResponse> out = tenantProductService.findAnyByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Busca produtos de qualquer status por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos encontrados
     */
    @GetMapping("/any/subcategory/{subcategoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> findAnyBySubcategory(@PathVariable Long subcategoryId) {
        List<ProductResponse> out = tenantProductService.findAnyBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Busca produtos de qualquer status por marca.
     *
     * @param brand marca
     * @return produtos encontrados
     */
    @GetMapping("/any/brand")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> findAnyByBrand(@RequestParam("brand") String brand) {
        List<ProductResponse> out = tenantProductService.findAnyByBrandIgnoreCase(brand).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Busca por filtros de produto.
     *
     * @param name nome
     * @param minPrice preço mínimo
     * @param maxPrice preço máximo
     * @param minStock estoque mínimo
     * @param maxStock estoque máximo
     * @return lista filtrada
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
        List<ProductResponse> out = tenantProductService.searchProducts(name, minPrice, maxPrice, minStock, maxStock).stream()
                .map(productApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Atualização parcial de produto.
     *
     * @param id id do produto
     * @param req payload patch
     * @return produto atualizado
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> patchUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdateRequest req
    ) {
        UpdateProductCommand cmd = buildUpdateCommandFrom(req);
        Product updated = tenantProductService.update(id, cmd);
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Atualização completa/lógica equivalente a replace.
     *
     * @param id id do produto
     * @param req payload put
     * @return produto atualizado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> putUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpsertRequest req
    ) {
        if (Boolean.TRUE.equals(req.clearSubcategory()) && req.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo",
                    400
            );
        }

        boolean clearSubcategory = Boolean.TRUE.equals(req.clearSubcategory());

        UpdateProductCommand cmd = new UpdateProductCommand(
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
                clearSubcategory,
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );

        Product updated = tenantProductService.update(id, cmd);
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Monta o command de update com regra correta para subcategoria.
     *
     * @param req request HTTP
     * @return command de update
     */
    private UpdateProductCommand buildUpdateCommandFrom(ProductUpdateRequest req) {
        boolean clearSubcategory = Boolean.TRUE.equals(req.clearSubcategory());

        if (req.subcategoryId() != null) {
            clearSubcategory = false;
        }

        return new UpdateProductCommand(
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
                clearSubcategory,
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );
    }

    /**
     * Resolve o accountId da identidade tenant autenticada.
     *
     * @return accountId atual
     */
    private Long requireCurrentAccountId() {
        Long accountId = tenantRequestIdentityService.getCurrentAccountId();

        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_REQUIRED,
                    "Não foi possível resolver a conta do tenant autenticado",
                    400
            );
        }

        return accountId;
    }
}