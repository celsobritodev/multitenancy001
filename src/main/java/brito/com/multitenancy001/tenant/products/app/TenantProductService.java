package brito.com.multitenancy001.tenant.products.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
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
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application Service (Tenant): Products.
 *
 * Nota importante (regra definitiva):
 * - A API mapeia Product -> DTO fora da transação (sem OpenSessionInView).
 * - Portanto, métodos de READ usados pela API precisam retornar entidades com relações inicializadas
 *   (category/subcategory/supplier), evitando LazyInitializationException.
 *
 * Estratégia:
 * - Repository expõe "read-safe" (EntityGraph / fetch join).
 * - Writes (create/update/toggle/cost) fazem save e retornam "re-fetch read-safe" por ID.
 *
 * Regras de write safety (DDL):
 * - SKU é UNIQUE somente para deleted=false (índice parcial).
 * - Service valida SKU levando deleted em conta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;

    // =========================================================
    // READ
    // =========================================================

    @TenantReadOnlyTx
    public Page<Product> findAll(Pageable pageable) {
        return tenantProductRepository.findAll(pageable);
    }

    @TenantReadOnlyTx
    public Product findById(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id, 404));
    }

    // =========================================================
    // CREATE
    // =========================================================

    @TenantTx
    public Product create(CreateProductCommand createProductCommand) {
        if (createProductCommand == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        Product product = fromCreateCommand(createProductCommand);
        validateProductForCreate(product);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            resolveCategoryAndSubcategory(product);
            resolveSupplier(product);
            validateSubcategoryBelongsToCategory(product);

            Product saved = tenantProductRepository.save(product);

            return tenantProductRepository.findWithRelationsById(saved.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após criação (ID: " + saved.getId() + ")", 500));
        });
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @TenantTx
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        if (updateProductCommand == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        // ✅ validação rápida ANTES da TX
        validateUpdateCommand(updateProductCommand);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            // ✅ não atualiza produto soft-deletado
            Product existing = tenantProductRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado com ID: " + id, 404));

            if (updateProductCommand.name() != null) {
                String name = updateProductCommand.name().trim();
                if (!name.isEmpty()) existing.setName(name);
            }

            if (updateProductCommand.description() != null) {
                existing.setDescription(updateProductCommand.description());
            }

            if (updateProductCommand.sku() != null) {
                String sku = updateProductCommand.sku().trim();
                if (!sku.isEmpty()) {
                    // ✅ SKU único respeitando deleted=false + excluindo o próprio ID
                    if (tenantProductRepository.existsSkuNotDeletedExcludingId(sku, id)) {
                        throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + sku, 409);
                    }
                    existing.setSku(sku);
                }
            }

            if (updateProductCommand.price() != null) {
                validatePrice(updateProductCommand.price());
                existing.updatePrice(updateProductCommand.price());
            }

            if (updateProductCommand.stockQuantity() != null) existing.setStockQuantity(updateProductCommand.stockQuantity());
            if (updateProductCommand.minStock() != null) existing.setMinStock(updateProductCommand.minStock());
            if (updateProductCommand.maxStock() != null) existing.setMaxStock(updateProductCommand.maxStock());

            if (updateProductCommand.costPrice() != null) existing.updateCostPrice(updateProductCommand.costPrice());
            if (updateProductCommand.brand() != null) existing.setBrand(updateProductCommand.brand());
            if (updateProductCommand.weightKg() != null) existing.setWeightKg(updateProductCommand.weightKg());
            if (updateProductCommand.dimensions() != null) existing.setDimensions(updateProductCommand.dimensions());
            if (updateProductCommand.barcode() != null) existing.setBarcode(updateProductCommand.barcode());
            if (updateProductCommand.active() != null) existing.setActive(updateProductCommand.active());

            if (updateProductCommand.categoryId() != null) {
                Category category = tenantCategoryRepository.findById(updateProductCommand.categoryId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
                existing.setCategory(category);
            }

            if (updateProductCommand.clearSubcategory()) {
                existing.setSubcategory(null);
            } else if (updateProductCommand.subcategoryId() != null) {
                Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(updateProductCommand.subcategoryId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", 404));
                existing.setSubcategory(sub);
            }

            if (updateProductCommand.supplierId() != null) {
                Supplier supplier = tenantSupplierRepository.findById(updateProductCommand.supplierId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado", 404));
                existing.setSupplier(supplier);
            }

            validateSubcategoryBelongsToCategory(existing);

            tenantProductRepository.save(existing);

            return tenantProductRepository.findWithRelationsById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após update (ID: " + id + ")", 500));
        });
    }

    // =========================================================
    // TOGGLE ACTIVE
    // =========================================================

    @TenantTx
    public Product toggleActive(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado com ID: " + id, 404));

            product.setActive(!Boolean.TRUE.equals(product.getActive()));
            tenantProductRepository.save(product);

            return tenantProductRepository.findWithRelationsById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após toggleActive (ID: " + id + ")", 500));
        });
    }

    // =========================================================
    // COST PRICE
    // =========================================================

    @TenantTx
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);

        if (costPrice == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "costPrice é obrigatório", 400);
        }

        if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo", 400);
        }

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado com ID: " + id, 404));

            product.updateCostPrice(costPrice);
            tenantProductRepository.save(product);

            return tenantProductRepository.findWithRelationsById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após updateCostPrice (ID: " + id + ")", 500));
        });
    }

    // =========================================================
    // READ endpoints da API
    // =========================================================

    @TenantReadOnlyTx
    public List<SupplierProductCountData> countProductsBySupplier() {
        List<Object[]> rows = tenantProductRepository.countProductsBySupplier();
        return rows.stream()
                .map(row -> new SupplierProductCountData((UUID) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @TenantReadOnlyTx
    public List<Product> findByCategoryId(Long categoryId) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        return tenantProductRepository.findByCategoryId(categoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        return tenantProductRepository.findBySubcategoryId(subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findBySupplierId(UUID supplierId) {
        if (supplierId == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "supplierId é obrigatório", 400);
        return tenantProductRepository.findBySupplierId(supplierId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByCategoryId(Long categoryId) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        return tenantProductRepository.findAnyByCategoryId(categoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        return tenantProductRepository.findAnyBySubcategoryId(subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        if (!StringUtils.hasText(brand)) throw new ApiException(ApiErrorCode.BRAND_REQUIRED, "brand é obrigatório", 400);
        return tenantProductRepository.findAnyByBrandIgnoreCase(brand.trim());
    }

    @TenantReadOnlyTx
    public BigDecimal calculateTotalInventoryValue() {
        return tenantProductRepository.calculateTotalInventoryValue();
    }

    @TenantReadOnlyTx
    public Long countLowStockProducts(Integer threshold) {
        return tenantProductRepository.countLowStockProducts(threshold);
    }

    @TenantReadOnlyTx
    public List<Product> searchProducts(String name, BigDecimal minPrice, BigDecimal maxPrice, Integer minStock, Integer maxStock) {
        return tenantProductRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }

    // =========================================================
    // Helpers (validators / resolvers)
    // =========================================================

    /**
     * Validação rápida do UpdateProductCommand.
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

    private void resolveSupplier(Product product) {
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            UUID supplierId = product.getSupplier().getId();
            Supplier supplier = tenantSupplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND,
                            "Fornecedor não encontrado com ID: " + supplierId, 404));
            product.setSupplier(supplier);
        }
    }

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

    private void validateSubcategoryBelongsToCategory(Product product) {
        if (product.getSubcategory() == null) return;

        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        if (product.getSubcategory().getCategory() == null || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria sem categoria associada (cadastro inconsistente)", 409);
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY, "Subcategoria não pertence à categoria informada", 409);
        }
    }

    private void validateProductForCreate(Product product) {
        if (product == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

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

        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getActive() == null) product.setActive(true);
        if (product.getDeleted() == null) product.setDeleted(false);
    }

    private void validatePrice(BigDecimal price) {
        if (price == null) throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "price é obrigatório", 400);
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE, "price não pode ser negativo", 400);
        }
    }

    private String requireBoundTenantSchema() {
        String tenantSchema = TenantContext.getOrNull();
        if (tenantSchema == null) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "TenantContext não está bindado (tenantSchema=null). Operação requer contexto TENANT.", 500);
        }
        return tenantSchema;
    }

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
        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getActive() == null) product.setActive(true);

        return product;
    }
}