package brito.com.multitenancy001.tenant.products.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
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
 * Controller HTTP do módulo de produtos no contexto tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Receber requests HTTP e devolver responses HTTP.</li>
 *   <li>Converter DTOs de request em commands da camada de aplicação.</li>
 *   <li>Delegar regras de negócio ao service de aplicação.</li>
 *   <li>Nunca manipular diretamente entidades de domínio para mutação.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>O controller é apenas boundary HTTP.</li>
 *   <li>O create path deve sempre passar por {@link TenantProductService#create(CreateProductCommand, String)}.</li>
 *   <li>O controller resolve explicitamente o {@code accountId} e o {@code tenantSchema} do contexto atual.</li>
 *   <li>O enforcement de quota acontece antes da transação de escrita, dentro da camada APP.</li>
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
     * Busca um produto por id no escopo do tenant atual.
     *
     * @param id id do produto
     * @return response com o produto encontrado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        log.info("Recebida requisição para buscar produto por id. productId={}", id);

        Product product = tenantProductService.findById(id);

        log.info("Produto carregado com sucesso. productId={}", id);
        return ResponseEntity.ok(productApiMapper.toResponse(product));
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
        log.info(
                "Recebida requisição para listar produtos paginados. pageNumber={}, pageSize={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize()
        );

        Page<ProductResponse> page = tenantProductService.findAll(pageable)
                .map(productApiMapper::toResponse);

        log.info(
                "Listagem paginada de produtos concluída. pageNumber={}, pageSize={}, returnedElements={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize(),
                page.getNumberOfElements()
        );

        return ResponseEntity.ok(page);
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
        log.info("Recebida requisição para listar produtos por categoria. categoryId={}", categoryId);

        List<ProductResponse> out = tenantProductService.findByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Listagem por categoria concluída. categoryId={}, returnedElements={}", categoryId, out.size());
        return ResponseEntity.ok(out);
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
        log.info("Recebida requisição para listar produtos por subcategoria. subcategoryId={}", subcategoryId);

        List<ProductResponse> out = tenantProductService.findBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Listagem por subcategoria concluída. subcategoryId={}, returnedElements={}", subcategoryId, out.size());
        return ResponseEntity.ok(out);
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
        log.info("Recebida requisição para listar produtos por fornecedor. supplierId={}", supplierId);

        List<ProductResponse> out = tenantProductService.findBySupplierId(supplierId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Listagem por fornecedor concluída. supplierId={}, returnedElements={}", supplierId, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Retorna contagem agregada de produtos por fornecedor.
     *
     * @return lista agregada por fornecedor
     */
    @GetMapping("/count-by-supplier")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_READ.asAuthority())")
    public ResponseEntity<List<SupplierProductCountResponse>> countBySupplier() {
        log.info("Recebida requisição para contagem de produtos por fornecedor.");

        List<SupplierProductCountResponse> out = tenantProductService.countProductsBySupplier().stream()
                .map(row -> new SupplierProductCountResponse(row.supplierId(), row.productCount()))
                .toList();

        log.info("Contagem por fornecedor concluída. returnedElements={}", out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Retorna o valor total do inventário do tenant.
     *
     * @return valor total do inventário
     */
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        log.info("Recebida requisição para consultar valor total do inventário.");

        BigDecimal value = tenantProductService.calculateTotalInventoryValue();
        BigDecimal normalizedValue = value != null ? value : BigDecimal.ZERO;

        log.info("Valor total do inventário calculado com sucesso. inventoryValue={}", normalizedValue);
        return ResponseEntity.ok(normalizedValue);
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
        log.info("Recebida requisição para contar produtos com estoque baixo. threshold={}", threshold);

        Long count = tenantProductService.countLowStockProducts(threshold);
        Long normalizedCount = count != null ? count : 0L;

        log.info("Contagem de low stock concluída com sucesso. threshold={}, count={}", threshold, normalizedCount);
        return ResponseEntity.ok(normalizedCount);
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
        log.info("Recebida requisição para alternar status ativo do produto. productId={}", id);

        Product updated = tenantProductService.toggleActive(id);

        log.info(
                "Status ativo do produto alterado com sucesso via API. productId={}, active={}",
                updated.getId(),
                updated.getActive()
        );

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
        log.info("Recebida requisição para atualizar costPrice do produto. productId={}, costPrice={}", id, costPrice);

        Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);

        log.info(
                "CostPrice do produto atualizado com sucesso via API. productId={}, costPrice={}",
                updatedProduct.getId(),
                updatedProduct.getCostPrice()
        );

        return ResponseEntity.ok(productApiMapper.toResponse(updatedProduct));
    }

    /**
     * Cria produto detalhado no tenant atual.
     *
     * <p><b>Fluxo V30 obrigatório:</b></p>
     * <ol>
     *   <li>Resolver {@code accountId} da identidade autenticada.</li>
     *   <li>Resolver {@code tenantSchema} do contexto atual.</li>
     *   <li>Montar {@link CreateProductCommand}.</li>
     *   <li>Delegar para {@link TenantProductService#create(CreateProductCommand, String)}.</li>
     * </ol>
     *
     * <p><b>Regra crítica:</b></p>
     * <ul>
     *   <li>O controller não pode chamar write service diretamente.</li>
     *   <li>O create path deve passar pelo service de orquestração para garantir quota enforcement.</li>
     * </ul>
     *
     * @param req payload HTTP de criação
     * @return produto criado
     */
    @PostMapping("/detailed")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_PRODUCT_WRITE.asAuthority())")
    public ResponseEntity<ProductResponse> createDetailedProduct(@Valid @RequestBody ProductUpsertRequest req) {
        Long accountId = requireCurrentAccountId();
        String tenantSchema = requireCurrentTenantSchema();

        log.info(
                "Recebida requisição de criação detalhada de produto. accountId={}, tenantSchema={}, sku={}, name={}",
                accountId,
                tenantSchema,
                req.sku(),
                req.name()
        );

        validateCreateRequest(req);

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

        Product savedProduct = tenantProductService.create(cmd, tenantSchema);

        log.info(
                "Produto criado via API com sucesso. accountId={}, tenantSchema={}, productId={}, sku={}",
                accountId,
                tenantSchema,
                savedProduct.getId(),
                savedProduct.getSku()
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
        log.info("Recebida requisição para buscar produtos de qualquer status por categoria. categoryId={}", categoryId);

        List<ProductResponse> out = tenantProductService.findAnyByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca any/category concluída. categoryId={}, returnedElements={}", categoryId, out.size());
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
        log.info(
                "Recebida requisição para buscar produtos de qualquer status por subcategoria. subcategoryId={}",
                subcategoryId
        );

        List<ProductResponse> out = tenantProductService.findAnyBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca any/subcategory concluída. subcategoryId={}, returnedElements={}", subcategoryId, out.size());
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
        log.info("Recebida requisição para buscar produtos de qualquer status por marca. brand={}", brand);

        List<ProductResponse> out = tenantProductService.findAnyByBrandIgnoreCase(brand).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca any/brand concluída. brand={}, returnedElements={}", brand, out.size());
        return ResponseEntity.ok(out);
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
        log.info(
                "Recebida requisição de busca de produtos. name={}, minPrice={}, maxPrice={}, minStock={}, maxStock={}",
                name,
                minPrice,
                maxPrice,
                minStock,
                maxStock
        );

        List<ProductResponse> out = tenantProductService.searchProducts(name, minPrice, maxPrice, minStock, maxStock).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca de produtos concluída. returnedElements={}", out.size());
        return ResponseEntity.ok(out);
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
        log.info("Recebida requisição de patch de produto. productId={}", id);

        UpdateProductCommand cmd = buildUpdateCommandFrom(req);
        Product updated = tenantProductService.update(id, cmd);

        log.info("Patch de produto concluído com sucesso. productId={}", updated.getId());
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
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
        log.info("Recebida requisição de put de produto. productId={}", id);

        if (Boolean.TRUE.equals(req.clearSubcategory()) && req.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo",
                    400
            );
        }

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
                Boolean.TRUE.equals(req.clearSubcategory()),
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );

        Product updated = tenantProductService.update(id, cmd);

        log.info("Put de produto concluído com sucesso. productId={}", updated.getId());
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Monta o command de update a partir do request de patch.
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
     * Valida regras mínimas do payload de criação no boundary HTTP.
     *
     * <p>As validações de domínio continuam pertencendo à camada APP/write service.
     * Aqui ficam apenas guards simples para evitar request semanticamente inconsistente.</p>
     *
     * @param req payload de criação
     */
    private void validateCreateRequest(ProductUpsertRequest req) {
        if (req == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        if (Boolean.TRUE.equals(req.clearSubcategory()) && req.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo",
                    400
            );
        }
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

    /**
     * Resolve o tenantSchema atual do contexto.
     *
     * @return tenantSchema atual
     */
    private String requireCurrentTenantSchema() {
        String tenantSchema = TenantContext.getOrNull();

        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(
                    ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "Não foi possível resolver o tenantSchema do contexto atual",
                    400
            );
        }

        return tenantSchema.trim();
    }
}