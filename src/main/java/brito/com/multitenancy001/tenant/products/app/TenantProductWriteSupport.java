package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Support compartilhado de escrita de produtos.
 *
 * <p>Centraliza regras reutilizadas pelos casos de uso de create/update:</p>
 * <ul>
 *   <li>montagem inicial da entidade</li>
 *   <li>validação de create/update</li>
 *   <li>resolução de category/subcategory/supplier</li>
 *   <li>validação de coerência entre categoria e subcategoria</li>
 *   <li>releitura da entidade com relações carregadas</li>
 * </ul>
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>remover multi-responsabilidade da fachada</li>
 *   <li>evitar duplicação entre casos de uso</li>
 *   <li>preservar logs e semântica de domínio</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantProductWriteSupport {

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;

    /**
     * Recarrega o produto com relações após persistência.
     *
     * @param productId id do produto
     * @param operation nome da operação para contexto de erro/log
     * @return produto com relações carregadas
     */
    public Product loadWithRelationsOrThrow(UUID productId, String operation) {
        return tenantProductRepository.findWithRelationsById(productId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado após " + operation + " (ID: " + productId + ")",
                        500
                ));
    }

    /**
     * Aplica alterações no produto existente.
     *
     * @param existing entidade existente
     * @param cmd command de update
     */
    public void applyUpdates(Product existing, UpdateProductCommand cmd) {
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
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.CATEGORY_NOT_FOUND,
                            "Categoria não encontrada",
                            404
                    ));
            existing.setCategory(category);
        }

        if (cmd.clearSubcategory()) {
            existing.setSubcategory(null);
        } else if (cmd.subcategoryId() != null) {
            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(cmd.subcategoryId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.SUBCATEGORY_NOT_FOUND,
                            "Subcategoria não encontrada",
                            404
                    ));
            existing.setSubcategory(sub);
        }

        if (cmd.supplierId() != null) {
            Supplier supplier = tenantSupplierRepository.findById(cmd.supplierId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.SUPPLIER_NOT_FOUND,
                            "Fornecedor não encontrado",
                            404
                    ));
            existing.setSupplier(supplier);
        }

        validateSubcategoryBelongsToCategory(existing);
    }

    /**
     * Resolve fornecedor completo do produto.
     *
     * @param product produto
     */
    public void resolveSupplier(Product product) {
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            UUID supplierId = product.getSupplier().getId();

            log.info("Resolvendo fornecedor do produto. supplierId={}", supplierId);

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
    public void resolveCategoryAndSubcategory(Product product) {
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        Long categoryId = product.getCategory().getId();

        log.info("Resolvendo categoria do produto. categoryId={}", categoryId);

        Category category = tenantCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.CATEGORY_NOT_FOUND,
                        "Categoria não encontrada",
                        404
                ));
        product.setCategory(category);

        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Long subcategoryId = product.getSubcategory().getId();

            log.info("Resolvendo subcategoria do produto. subcategoryId={}", subcategoryId);

            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(subcategoryId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.SUBCATEGORY_NOT_FOUND,
                            "Subcategoria não encontrada",
                            404
                    ));
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
    public void validateSubcategoryBelongsToCategory(Product product) {
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
    public void validateProductForCreate(Product product) {
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

        String normalizedName = product.getName().trim();
        String normalizedSku = product.getSku().trim();

        if (normalizedName.isEmpty()) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "name é obrigatório", 400);
        }

        if (normalizedSku.isEmpty()) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku é obrigatório", 400);
        }

        if (tenantProductRepository.existsBySkuAndDeletedFalse(normalizedSku)) {
            throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + normalizedSku, 409);
        }

        product.setName(normalizedName);
        product.setSku(normalizedSku);

        if (product.getDescription() != null) {
            product.setDescription(product.getDescription().trim());
        }

        if (product.getBrand() != null) {
            String normalizedBrand = product.getBrand().trim();
            product.setBrand(normalizedBrand.isEmpty() ? null : normalizedBrand);
        }

        if (product.getBarcode() != null) {
            String normalizedBarcode = product.getBarcode().trim();
            product.setBarcode(normalizedBarcode.isEmpty() ? null : normalizedBarcode);
        }

        if (product.getDimensions() != null) {
            String normalizedDimensions = product.getDimensions().trim();
            product.setDimensions(normalizedDimensions.isEmpty() ? null : normalizedDimensions);
        }

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
    public void validatePrice(BigDecimal price) {
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
    public void validateUpdateCommand(UpdateProductCommand updateProductCommand) {
        if (updateProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

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

        if (updateProductCommand.costPrice() != null
                && updateProductCommand.costPrice().compareTo(BigDecimal.ZERO) < 0) {
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
    public Product fromCreateCommand(CreateProductCommand createProductCommand) {
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