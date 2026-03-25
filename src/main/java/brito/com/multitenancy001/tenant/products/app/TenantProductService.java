package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.subscription.app.TenantQuotaEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service do módulo de produtos no contexto tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Executar operações de leitura no schema tenant.</li>
 *   <li>Orquestrar validações prévias antes do write-path.</li>
 *   <li>Executar enforcement de quota antes da criação.</li>
 *   <li>Delegar escritas transacionais para {@link TenantProductWriteService}.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Este service não deve depender de self-invocation para aplicar TX.</li>
 *   <li>O pre-check de quota ocorre fora da transação tenant de escrita.</li>
 *   <li>O save efetivo ocorre em bean separado com boundary transacional explícito.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final TenantProductWriteService tenantProductWriteService;

    /**
     * Lista produtos paginados.
     *
     * @param pageable paginação
     * @return página de produtos
     */
    @TenantReadOnlyTx
    public Page<Product> findAll(Pageable pageable) {
        log.info(
                "Listando produtos paginados no tenant. pageNumber={}, pageSize={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize()
        );

        Page<Product> page = tenantProductRepository.findAll(pageable);

        log.info(
                "Listagem paginada concluída. pageNumber={}, pageSize={}, returnedElements={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize(),
                page.getNumberOfElements()
        );

        return page;
    }

    /**
     * Busca produto por id com relações carregadas.
     *
     * @param id id do produto
     * @return produto encontrado
     */
    @TenantReadOnlyTx
    public Product findById(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }

        log.info("Buscando produto por id no tenant. productId={}", id);

        Product product = tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        log.info("Produto encontrado com sucesso. productId={}, sku={}", product.getId(), product.getSku());
        return product;
    }

    /**
     * Cria um novo produto com validação prévia e enforcement de quota.
     *
     * <p><b>Fluxo:</b></p>
     * <ol>
     *   <li>Valida payload e contexto.</li>
     *   <li>Executa quota enforcement fora da transação tenant de escrita.</li>
     *   <li>Delega o save para {@link TenantProductWriteService}.</li>
     * </ol>
     *
     * @param createProductCommand payload de criação
     * @param tenantSchema schema do tenant
     * @return produto criado com relações carregadas
     */
    public Product create(CreateProductCommand createProductCommand, String tenantSchema) {
        if (createProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        if (createProductCommand.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        String normalizedTenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        log.info(
                "Iniciando criação de produto (ORQUESTRAÇÃO). accountId={}, tenantSchema={}, sku={}, name={}",
                createProductCommand.accountId(),
                normalizedTenantSchema,
                createProductCommand.sku(),
                createProductCommand.name()
        );

        tenantQuotaEnforcementService.assertCanCreateProduct(
                createProductCommand.accountId(),
                normalizedTenantSchema
        );

        log.info(
                "Pre-check de quota concluído com sucesso para criação de produto. accountId={}, tenantSchema={}, sku={}",
                createProductCommand.accountId(),
                normalizedTenantSchema,
                createProductCommand.sku()
        );

        Product savedProduct = tenantProductWriteService.create(createProductCommand);

        log.info(
                "Criação de produto concluída com sucesso. accountId={}, tenantSchema={}, productId={}, sku={}",
                createProductCommand.accountId(),
                normalizedTenantSchema,
                savedProduct.getId(),
                savedProduct.getSku()
        );

        return savedProduct;
    }

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param updateProductCommand payload de update
     * @return produto atualizado
     */
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        log.info("Delegando atualização de produto para write service. productId={}", id);
        return tenantProductWriteService.update(id, updateProductCommand);
    }

    /**
     * Alterna status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    public Product toggleActive(UUID id) {
        log.info("Delegando toggleActive de produto para write service. productId={}", id);
        return tenantProductWriteService.toggleActive(id);
    }

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        log.info("Delegando updateCostPrice de produto para write service. productId={}, costPrice={}", id, costPrice);
        return tenantProductWriteService.updateCostPrice(id, costPrice);
    }

    /**
     * Conta produtos agrupados por fornecedor.
     *
     * @return lista com contagem por fornecedor
     */
    @TenantReadOnlyTx
    public List<SupplierProductCountData> countProductsBySupplier() {
        log.info("Contando produtos por fornecedor.");
        List<SupplierProductCountData> result = tenantProductRepository.countProductsBySupplier();
        log.info("Contagem por fornecedor concluída. returnedElements={}", result.size());
        return result;
    }

    /**
     * Busca produtos por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos da categoria
     */
    @TenantReadOnlyTx
    public List<Product> findByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        }

        log.info("Buscando produtos por categoria. categoryId={}", categoryId);
        List<Product> result = tenantProductRepository.findByCategoryId(categoryId);
        log.info("Busca por categoria concluída. categoryId={}, returnedElements={}", categoryId, result.size());
        return result;
    }

    /**
     * Busca produtos por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos da subcategoria
     */
    @TenantReadOnlyTx
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        }

        log.info("Buscando produtos por subcategoria. subcategoryId={}", subcategoryId);
        List<Product> result = tenantProductRepository.findBySubcategoryId(subcategoryId);
        log.info("Busca por subcategoria concluída. subcategoryId={}, returnedElements={}", subcategoryId, result.size());
        return result;
    }

    /**
     * Busca produtos por fornecedor.
     *
     * @param supplierId id do fornecedor
     * @return produtos do fornecedor
     */
    @TenantReadOnlyTx
    public List<Product> findBySupplierId(UUID supplierId) {
        if (supplierId == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "supplierId é obrigatório", 400);
        }

        log.info("Buscando produtos por fornecedor. supplierId={}", supplierId);
        List<Product> result = tenantProductRepository.findBySupplierId(supplierId);
        log.info("Busca por fornecedor concluída. supplierId={}, returnedElements={}", supplierId, result.size());
        return result;
    }

    /**
     * Busca produtos de qualquer status por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos encontrados
     */
    @TenantReadOnlyTx
    public List<Product> findAnyByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        }

        log.info("Buscando produtos any por categoria. categoryId={}", categoryId);
        List<Product> result = tenantProductRepository.findAnyByCategoryId(categoryId);
        log.info("Busca any por categoria concluída. categoryId={}, returnedElements={}", categoryId, result.size());
        return result;
    }

    /**
     * Busca produtos de qualquer status por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos encontrados
     */
    @TenantReadOnlyTx
    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        }

        log.info("Buscando produtos any por subcategoria. subcategoryId={}", subcategoryId);
        List<Product> result = tenantProductRepository.findAnyBySubcategoryId(subcategoryId);
        log.info("Busca any por subcategoria concluída. subcategoryId={}, returnedElements={}", subcategoryId, result.size());
        return result;
    }

    /**
     * Busca produtos por marca.
     *
     * @param brand marca
     * @return produtos encontrados
     */
    @TenantReadOnlyTx
    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        if (!StringUtils.hasText(brand)) {
            throw new ApiException(ApiErrorCode.BRAND_REQUIRED, "brand é obrigatório", 400);
        }

        String normalizedBrand = brand.trim();

        log.info("Buscando produtos any por marca. brand={}", normalizedBrand);
        List<Product> result = tenantProductRepository.findAnyByBrandIgnoreCase(normalizedBrand);
        log.info("Busca any por marca concluída. brand={}, returnedElements={}", normalizedBrand, result.size());
        return result;
    }

    /**
     * Calcula valor total do inventário.
     *
     * @return valor total do inventário
     */
    @TenantReadOnlyTx
    public BigDecimal calculateTotalInventoryValue() {
        log.info("Calculando valor total do inventário.");
        BigDecimal result = tenantProductRepository.calculateTotalInventoryValue();
        log.info("Valor total do inventário calculado. inventoryValue={}", result);
        return result;
    }

    /**
     * Conta produtos abaixo do estoque mínimo.
     *
     * @param threshold limite usado na consulta
     * @return quantidade de produtos
     */
    @TenantReadOnlyTx
    public Long countLowStockProducts(Integer threshold) {
        log.info("Contando produtos com estoque baixo. threshold={}", threshold);
        Long result = tenantProductRepository.countLowStockProducts(threshold);
        log.info("Contagem de low stock concluída. threshold={}, count={}", threshold, result);
        return result;
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
    @TenantReadOnlyTx
    public List<Product> searchProducts(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        log.info(
                "Executando busca de produtos por filtros. name={}, minPrice={}, maxPrice={}, minStock={}, maxStock={}",
                name,
                minPrice,
                maxPrice,
                minStock,
                maxStock
        );

        List<Product> result = tenantProductRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);

        log.info("Busca de produtos por filtros concluída. returnedElements={}", result.size());
        return result;
    }
}