package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.subscription.app.TenantQuotaEnforcementService;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service (Tenant): Products.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar operações de leitura/escrita de produtos no schema tenant.</li>
 *   <li>Validar payload, relações e coerência de domínio.</li>
 *   <li>Executar enforcement de quota antes da criação de produto.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Métodos com {@code @TenantTx}/{@code @TenantReadOnlyTx} já executam em transação tenant.</li>
 *   <li>Não abrir {@code TenantSchemaUnitOfWork} manualmente dentro destes métodos.</li>
 *   <li>O enforcement de quota precisa ocorrer no write-path canônico.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;

    // =========================================================
    // READ
    // =========================================================

    /**
     * Lista produtos paginados.
     *
     * @param pageable paginação
     * @return página de produtos
     */
    @TenantReadOnlyTx
    public Page<Product> findAll(Pageable pageable) {
        return tenantProductRepository.findAll(pageable);
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

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));
    }

    // =========================================================
    // CREATE
    // =========================================================

    /**
     * Cria um novo produto com validação completa e enforcement de quota.
     *
     * @param createProductCommand payload de criação
     * @return produto criado com relações carregadas
     */
    @TenantTx
    public Product create(CreateProductCommand createProductCommand) {
        if (createProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        if (createProductCommand.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info(
                "Iniciando criação de produto. accountId={}, sku={}, name={}",
                createProductCommand.accountId(),
                createProductCommand.sku(),
                createProductCommand.name()
        );

        tenantQuotaEnforcementService.assertCanCreateProduct(createProductCommand.accountId());

        Product product = fromCreateCommand(createProductCommand);
        validateProductForCreate(product);

        resolveCategoryAndSubcategory(product);
        resolveSupplier(product);
        validateSubcategoryBelongsToCategory(product);

        Product saved = tenantProductRepository.save(product);

        log.info(
                "Produto criado com sucesso. accountId={}, productId={}, sku={}",
                createProductCommand.accountId(),
                saved.getId(),
                saved.getSku()
        );

        return tenantProductRepository.findWithRelationsById(saved.getId())
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado após criação (ID: " + saved.getId() + ")",
                        500
                ));
    }

    // =========================================================
    // UPDATE
    // =========================================================

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param updateProductCommand payload de update
     * @return produto atualizado com relações carregadas
     */
    @TenantTx
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }
        if (updateProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        log.info("Atualizando produto. id={}", id);

        validateUpdateCommand(updateProductCommand);

        Product existing = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        applyUpdates(existing, updateProductCommand);
        tenantProductRepository.save(existing);

        log.info("Produto atualizado com sucesso. id={}", id);

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado após update (ID: " + id + ")",
                        500
                ));
    }

    // =========================================================
    // TOGGLE ACTIVE
    // =========================================================

    /**
     * Alterna o status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    @TenantTx
    public Product toggleActive(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }

        Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        product.setActive(!Boolean.TRUE.equals(product.getActive()));
        tenantProductRepository.save(product);

        log.info("Status de ativo do produto alterado com sucesso. id={}, active={}", id, product.getActive());

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado após toggleActive (ID: " + id + ")",
                        500
                ));
    }

    // =========================================================
    // COST PRICE
    // =========================================================

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    @TenantTx
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }

        if (costPrice == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "costPrice é obrigatório", 400);
        }

        if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo", 400);
        }

        Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        product.updateCostPrice(costPrice);
        tenantProductRepository.save(product);

        log.info("Custo do produto atualizado com sucesso. id={}, costPrice={}", id, costPrice);

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado após updateCostPrice (ID: " + id + ")",
                        500
                ));
    }

    // =========================================================
    // READ endpoints da API
    // =========================================================

    /**
     * Conta produtos agrupados por fornecedor.
     *
     * @return lista com contagem por fornecedor
     */
    @TenantReadOnlyTx
    public List<SupplierProductCountData> countProductsBySupplier() {
        return tenantProductRepository.countProductsBySupplier();
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
        return tenantProductRepository.findByCategoryId(categoryId);
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
        return tenantProductRepository.findBySubcategoryId(subcategoryId);
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
        return tenantProductRepository.findBySupplierId(supplierId);
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
        return tenantProductRepository.findAnyByCategoryId(categoryId);
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
        return tenantProductRepository.findAnyBySubcategoryId(subcategoryId);
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
        return tenantProductRepository.findAnyByBrandIgnoreCase(brand.trim());
    }

    /**
     * Calcula valor total do inventário.
     *
     * @return valor total do inventário
     */
    @TenantReadOnlyTx
    public BigDecimal calculateTotalInventoryValue() {
        return tenantProductRepository.calculateTotalInventoryValue();
    }

    /**
     * Conta produtos abaixo do estoque mínimo.
     *
     * @param threshold limite usado na consulta
     * @return quantidade de produtos
     */
    @TenantReadOnlyTx
    public Long countLowStockProducts(Integer threshold) {
        return tenantProductRepository.countLowStockProducts(threshold);
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
    public List<Product> searchProducts(String name, BigDecimal minPrice, BigDecimal maxPrice, Integer minStock, Integer maxStock) {
        return tenantProductRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Aplica alterações no produto existente.
     *
     * @param existing entidade existente
     * @param cmd command de update
     */
    private void applyUpdates(Product existing, UpdateProductCommand cmd) {
        if (cmd.name() != null) {
            String name = cmd.name().trim();
            if (!name.isEmpty()) {
                existing.setName(name);
            }
        }

        if (cmd.description() != null) {
            existing.setDescription(cmd.description());
        }

        if (cmd.sku() != null) {
            String sku = cmd.sku().trim();
            if (!sku.isEmpty()) {
                if (tenantProductRepository.existsSkuNotDeletedExcludingId(sku, existing.getId())) {
                    throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + sku, 409);
                }
                existing.setSku(sku);
            }
        }

        if (cmd.price() != null) {
            validatePrice(cmd.price());
            existing.updatePrice(cmd.price());
        }

        if (cmd.stockQuantity() != null) {
            existing.setStockQuantity(cmd.stockQuantity());
        }
        if (cmd.minStock() != null) {
            existing.setMinStock(cmd.minStock());
        }
        if (cmd.maxStock() != null) {
            existing.setMaxStock(cmd.maxStock());
        }
        if (cmd.costPrice() != null) {
            existing.updateCostPrice(cmd.costPrice());
        }
        if (cmd.brand() != null) {
            existing.setBrand(cmd.brand());
        }
        if (cmd.weightKg() != null) {
            existing.setWeightKg(cmd.weightKg());
        }
        if (cmd.dimensions() != null) {
            existing.setDimensions(cmd.dimensions());
        }
        if (cmd.barcode() != null) {
            existing.setBarcode(cmd.barcode());
        }
        if (cmd.active() != null) {
            existing.setActive(cmd.active());
        }

        if (cmd.categoryId() != null) {
            Category category = tenantCategoryRepository.findById(cmd.categoryId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
            existing.setCategory(category);
        }

        if (cmd.clearSubcategory()) {
            existing.setSubcategory(null);
        } else if (cmd.subcategoryId() != null) {
            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(cmd.subcategoryId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", 404));
            existing.setSubcategory(sub);
        }

        if (cmd.supplierId() != null) {
            Supplier supplier = tenantSupplierRepository.findById(cmd.supplierId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado", 404));
            existing.setSupplier(supplier);
        }

        validateSubcategoryBelongsToCategory(existing);
    }

    /**
     * Resolve fornecedor completo do produto.
     *
     * @param product produto
     */
    private void resolveSupplier(Product product) {
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            UUID supplierId = product.getSupplier().getId();
            Supplier supplier = tenantSupplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.SUPPLIER_NOT_FOUND,
                            "Fornecedor não encontrado com ID: " + supplierId,
                            404
                    ));
            product.setSupplier(supplier);
        }
    }

    /**
     * Resolve categoria e subcategoria completas do produto.
     *
     * @param product produto
     */
    private void resolveCategoryAndSubcategory(Product product) {
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        Category category = tenantCategoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
        product.setCategory(category);

        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(product.getSubcategory().getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", 404));
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }
    }

    /**
     * Valida se a subcategoria pertence à categoria informada.
     *
     * @param product produto
     */
    private void validateSubcategoryBelongsToCategory(Product product) {
        if (product.getSubcategory() == null) {
            return;
        }

        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        if (product.getSubcategory().getCategory() == null || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria sem categoria associada (cadastro inconsistente)",
                    409
            );
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria não pertence à categoria informada",
                    409
            );
        }
    }

    /**
     * Valida integridade do produto antes da criação.
     *
     * @param product produto a validar
     */
    private void validateProductForCreate(Product product) {
        if (product == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        if (!StringUtils.hasText(product.getName())) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "name é obrigatório", 400);
        }

        if (!StringUtils.hasText(product.getSku())) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku é obrigatório", 400);
        }

        if (product.getPrice() == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "price é obrigatório", 400);
        }

        validatePrice(product.getPrice());

        String sku = product.getSku().trim();
        if (sku.isEmpty()) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku é obrigatório", 400);
        }

        if (tenantProductRepository.existsBySkuAndDeletedFalse(sku)) {
            throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + sku, 409);
        }

        product.setName(product.getName().trim());
        product.setSku(sku);

        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0);
        }
        if (product.getActive() == null) {
            product.setActive(true);
        }
        if (product.getDeleted() == null) {
            product.setDeleted(false);
        }
    }

    /**
     * Valida preço.
     *
     * @param price preço a validar
     */
    private void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "price é obrigatório", 400);
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE, "price não pode ser negativo", 400);
        }
    }

    /**
     * Valida o command de update.
     *
     * @param updateProductCommand command de update
     */
    private void validateUpdateCommand(UpdateProductCommand updateProductCommand) {
        if (updateProductCommand.clearSubcategory() && updateProductCommand.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo",
                    400
            );
        }

        if (updateProductCommand.sku() != null && updateProductCommand.sku().trim().isEmpty()) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku não pode ser vazio", 400);
        }

        if (updateProductCommand.name() != null && updateProductCommand.name().trim().isEmpty()) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "name não pode ser vazio", 400);
        }

        if (updateProductCommand.price() != null) {
            validatePrice(updateProductCommand.price());
        }

        if (updateProductCommand.costPrice() != null && updateProductCommand.costPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo", 400);
        }

        if (updateProductCommand.stockQuantity() != null && updateProductCommand.stockQuantity() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "stockQuantity não pode ser negativo", 400);
        }

        if (updateProductCommand.minStock() != null && updateProductCommand.minStock() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "minStock não pode ser negativo", 400);
        }

        if (updateProductCommand.maxStock() != null && updateProductCommand.maxStock() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "maxStock não pode ser negativo", 400);
        }
    }

    /**
     * Constrói entidade produto a partir do command de criação.
     *
     * @param createProductCommand command de criação
     * @return entidade de produto
     */
    private Product fromCreateCommand(CreateProductCommand createProductCommand) {
        Product product = new Product();
        product.setName(createProductCommand.name());
        product.setDescription(createProductCommand.description());
        product.setSku(createProductCommand.sku());
        product.setPrice(createProductCommand.price());
        product.setStockQuantity(createProductCommand.stockQuantity());
        product.setMinStock(createProductCommand.minStock());
        product.setMaxStock(createProductCommand.maxStock());
        product.setCostPrice(createProductCommand.costPrice());
        product.setBrand(createProductCommand.brand());
        product.setWeightKg(createProductCommand.weightKg());
        product.setDimensions(createProductCommand.dimensions());
        product.setBarcode(createProductCommand.barcode());
        product.setActive(createProductCommand.active());

        Category category = new Category();
        category.setId(createProductCommand.categoryId());
        product.setCategory(category);

        if (createProductCommand.subcategoryId() != null) {
            Subcategory sub = new Subcategory();
            sub.setId(createProductCommand.subcategoryId());
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }

        if (createProductCommand.supplierId() != null) {
            Supplier supplier = new Supplier();
            supplier.setId(createProductCommand.supplierId());
            product.setSupplier(supplier);
        } else {
            product.setSupplier(null);
        }

        product.setDeleted(false);

        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0);
        }
        if (product.getActive() == null) {
            product.setActive(true);
        }

        return product;
    }
}