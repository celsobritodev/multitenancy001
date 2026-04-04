package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData;
import brito.com.multitenancy001.tenant.products.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina do módulo de produtos no contexto tenant.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com os chamadores existentes.</li>
 *   <li>Delegar queries para serviços de leitura especializados.</li>
 *   <li>Delegar mutações para serviço de command/orquestração especializado.</li>
 *   <li>Separar consultas analíticas/inventory das consultas de catálogo.</li>
 * </ul>
 *
 * <p>Esta classe não deve concentrar regra de negócio pesada.
 * Ela atua apenas como ponto de entrada compatível da camada APP.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantProductQueryService tenantProductQueryService;
    private final TenantProductCommandService tenantProductCommandService;
    private final TenantProductInventoryQueryService tenantProductInventoryQueryService;

    /**
     * Lista produtos paginados.
     *
     * @param pageable paginação
     * @return página de produtos
     */
    public Page<Product> findAll(Pageable pageable) {
        log.debug(
                "PRODUCT_SERVICE_FACADE_FIND_ALL | pageNumber={} | pageSize={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize()
        );
        return tenantProductQueryService.findAll(pageable);
    }

    /**
     * Busca produto por id com relações carregadas.
     *
     * @param id id do produto
     * @return produto encontrado
     */
    public Product findById(UUID id) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_BY_ID | productId={}", id);
        return tenantProductQueryService.findById(id);
    }

    /**
     * Cria um novo produto com validação prévia e enforcement de quota.
     *
     * @param createProductCommand payload de criação
     * @param tenantSchema schema do tenant
     * @return produto criado com relações carregadas
     */
    public Product create(CreateProductCommand createProductCommand, String tenantSchema) {
        log.debug(
                "PRODUCT_SERVICE_FACADE_CREATE | accountId={} | tenantSchema={}",
                createProductCommand != null ? createProductCommand.accountId() : null,
                tenantSchema
        );
        return tenantProductCommandService.create(createProductCommand, tenantSchema);
    }

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param updateProductCommand payload de update
     * @return produto atualizado
     */
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        log.debug("PRODUCT_SERVICE_FACADE_UPDATE | productId={}", id);
        return tenantProductCommandService.update(id, updateProductCommand);
    }

    /**
     * Alterna status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    public Product toggleActive(UUID id) {
        log.debug("PRODUCT_SERVICE_FACADE_TOGGLE_ACTIVE | productId={}", id);
        return tenantProductCommandService.toggleActive(id);
    }

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        log.debug(
                "PRODUCT_SERVICE_FACADE_UPDATE_COST_PRICE | productId={} | costPrice={}",
                id,
                costPrice
        );
        return tenantProductCommandService.updateCostPrice(id, costPrice);
    }

    /**
     * Conta produtos agrupados por fornecedor.
     *
     * @return lista com contagem por fornecedor
     */
    public List<SupplierProductCountData> countProductsBySupplier() {
        log.debug("PRODUCT_SERVICE_FACADE_COUNT_BY_SUPPLIER");
        return tenantProductInventoryQueryService.countProductsBySupplier();
    }

    /**
     * Busca produtos por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos da categoria
     */
    public List<Product> findByCategoryId(Long categoryId) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_BY_CATEGORY | categoryId={}", categoryId);
        return tenantProductQueryService.findByCategoryId(categoryId);
    }

    /**
     * Busca produtos por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos da subcategoria
     */
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_BY_SUBCATEGORY | subcategoryId={}", subcategoryId);
        return tenantProductQueryService.findBySubcategoryId(subcategoryId);
    }

    /**
     * Busca produtos por fornecedor.
     *
     * @param supplierId id do fornecedor
     * @return produtos do fornecedor
     */
    public List<Product> findBySupplierId(UUID supplierId) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_BY_SUPPLIER | supplierId={}", supplierId);
        return tenantProductQueryService.findBySupplierId(supplierId);
    }

    /**
     * Busca produtos de qualquer status por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos encontrados
     */
    public List<Product> findAnyByCategoryId(Long categoryId) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_ANY_BY_CATEGORY | categoryId={}", categoryId);
        return tenantProductQueryService.findAnyByCategoryId(categoryId);
    }

    /**
     * Busca produtos de qualquer status por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos encontrados
     */
    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_ANY_BY_SUBCATEGORY | subcategoryId={}", subcategoryId);
        return tenantProductQueryService.findAnyBySubcategoryId(subcategoryId);
    }

    /**
     * Busca produtos por marca.
     *
     * @param brand marca
     * @return produtos encontrados
     */
    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        log.debug("PRODUCT_SERVICE_FACADE_FIND_ANY_BY_BRAND | brand={}", brand);
        return tenantProductQueryService.findAnyByBrandIgnoreCase(brand);
    }

    /**
     * Calcula valor total do inventário.
     *
     * @return valor total do inventário
     */
    public BigDecimal calculateTotalInventoryValue() {
        log.debug("PRODUCT_SERVICE_FACADE_CALCULATE_TOTAL_INVENTORY_VALUE");
        return tenantProductInventoryQueryService.calculateTotalInventoryValue();
    }

    /**
     * Conta produtos abaixo do estoque mínimo.
     *
     * @param threshold limite usado na consulta
     * @return quantidade de produtos
     */
    public Long countLowStockProducts(Integer threshold) {
        log.debug("PRODUCT_SERVICE_FACADE_COUNT_LOW_STOCK | threshold={}", threshold);
        return tenantProductInventoryQueryService.countLowStockProducts(threshold);
    }

    /**
     * Executa busca por filtros.
     *
     * @param name nome
     * @param minPrice preço mínimo
     * @param maxPrice preço máximo
     * @param minStock estoque mínimo
     * @param maxStock estoque máximo
     * @return lista de produtos
     */
    public List<Product> searchProducts(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        log.debug(
                "PRODUCT_SERVICE_FACADE_SEARCH_PRODUCTS | name={} | minPrice={} | maxPrice={} | minStock={} | maxStock={}",
                name,
                minPrice,
                maxPrice,
                minStock,
                maxStock
        );
        return tenantProductQueryService.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }
}