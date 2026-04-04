package brito.com.multitenancy001.tenant.products.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpdateRequest;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.products.api.dto.SupplierProductCountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina HTTP do módulo de produtos no contexto tenant.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar as rotas existentes.</li>
 *   <li>Manter compatibilidade com clientes e testes E2E.</li>
 *   <li>Delegar responsabilidades para componentes HTTP especializados.</li>
 * </ul>
 *
 * <p>Esta classe não deve concentrar regra de negócio nem montagem pesada
 * de commands/responses. Ela atua apenas como entrypoint compatível.</p>
 */
@RestController
@RequestMapping("/api/tenant/products")
@RequiredArgsConstructor
@Slf4j
public class TenantProductController {

    private final TenantProductQueryControllerDelegate queryDelegate;
    private final TenantProductCommandControllerDelegate commandDelegate;
    private final TenantProductInventoryControllerDelegate inventoryDelegate;

    /**
     * Busca um produto por id no escopo do tenant atual.
     *
     * @param id id do produto
     * @return response com o produto encontrado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        log.debug("PRODUCT_CONTROLLER_FACADE_GET_BY_ID | productId={}", id);
        return queryDelegate.getById(id);
    }

    /**
     * Lista produtos paginados do tenant atual.
     *
     * @param pageable paginação
     * @return página de produtos
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<Page<ProductResponse>> list(Pageable pageable) {
        log.debug(
                "PRODUCT_CONTROLLER_FACADE_LIST | pageNumber={} | pageSize={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize()
        );
        return queryDelegate.list(pageable);
    }

    /**
     * Lista produtos por categoria.
     *
     * @param categoryId id da categoria
     * @return lista de produtos da categoria
     */
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listByCategory(@PathVariable Long categoryId) {
        log.debug("PRODUCT_CONTROLLER_FACADE_LIST_BY_CATEGORY | categoryId={}", categoryId);
        return queryDelegate.listByCategory(categoryId);
    }

    /**
     * Lista produtos por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return lista de produtos da subcategoria
     */
    @GetMapping("/subcategory/{subcategoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listBySubcategory(@PathVariable Long subcategoryId) {
        log.debug("PRODUCT_CONTROLLER_FACADE_LIST_BY_SUBCATEGORY | subcategoryId={}", subcategoryId);
        return queryDelegate.listBySubcategory(subcategoryId);
    }

    /**
     * Lista produtos por fornecedor.
     *
     * @param supplierId id do fornecedor
     * @return lista de produtos do fornecedor
     */
    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<ProductResponse>> listBySupplier(@PathVariable UUID supplierId) {
        log.debug("PRODUCT_CONTROLLER_FACADE_LIST_BY_SUPPLIER | supplierId={}", supplierId);
        return queryDelegate.listBySupplier(supplierId);
    }

    /**
     * Retorna contagem agregada de produtos por fornecedor.
     *
     * @return lista agregada por fornecedor
     */
    @GetMapping("/count-by-supplier")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<SupplierProductCountResponse>> countBySupplier() {
        log.debug("PRODUCT_CONTROLLER_FACADE_COUNT_BY_SUPPLIER");
        return inventoryDelegate.countBySupplier();
    }

    /**
     * Retorna o valor total do inventário do tenant.
     *
     * @return valor total do inventário
     */
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        log.debug("PRODUCT_CONTROLLER_FACADE_INVENTORY_VALUE");
        return inventoryDelegate.getTotalInventoryValue();
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
        log.debug("PRODUCT_CONTROLLER_FACADE_LOW_STOCK_COUNT | threshold={}", threshold);
        return inventoryDelegate.countLowStock(threshold);
    }

    /**
     * Alterna o status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable UUID id) {
        log.debug("PRODUCT_CONTROLLER_FACADE_TOGGLE_ACTIVE | productId={}", id);
        return commandDelegate.toggleActive(id);
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
        log.debug("PRODUCT_CONTROLLER_FACADE_UPDATE_COST_PRICE | productId={} | costPrice={}", id, costPrice);
        return commandDelegate.updateCostPrice(id, costPrice);
    }

    /**
     * Cria produto detalhado no tenant atual.
     *
     * @param req payload HTTP de criação
     * @return produto criado
     */
    @PostMapping("/detailed")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> createDetailedProduct(@Valid @RequestBody ProductUpsertRequest req) {
        log.debug("PRODUCT_CONTROLLER_FACADE_CREATE_DETAILED");
        return commandDelegate.createDetailedProduct(req);
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
        log.debug("PRODUCT_CONTROLLER_FACADE_FIND_ANY_BY_CATEGORY | categoryId={}", categoryId);
        return queryDelegate.findAnyByCategory(categoryId);
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
        log.debug("PRODUCT_CONTROLLER_FACADE_FIND_ANY_BY_SUBCATEGORY | subcategoryId={}", subcategoryId);
        return queryDelegate.findAnyBySubcategory(subcategoryId);
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
        log.debug("PRODUCT_CONTROLLER_FACADE_FIND_ANY_BY_BRAND | brand={}", brand);
        return queryDelegate.findAnyByBrand(brand);
    }

    /**
     * Busca produtos por filtros.
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
        log.debug(
                "PRODUCT_CONTROLLER_FACADE_SEARCH | name={} | minPrice={} | maxPrice={} | minStock={} | maxStock={}",
                name,
                minPrice,
                maxPrice,
                minStock,
                maxStock
        );
        return queryDelegate.searchByName(name, minPrice, maxPrice, minStock, maxStock);
    }

    /**
     * Executa atualização parcial de produto.
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
        log.debug("PRODUCT_CONTROLLER_FACADE_PATCH_UPDATE | productId={}", id);
        return commandDelegate.patchUpdate(id, req);
    }

    /**
     * Executa atualização completa do produto.
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
        log.debug("PRODUCT_CONTROLLER_FACADE_PUT_UPDATE | productId={}", id);
        return commandDelegate.putUpdate(id, req);
    }
}