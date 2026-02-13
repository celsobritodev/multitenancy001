package brito.com.multitenancy001.tenant.products.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import brito.com.multitenancy001.tenant.products.api.dto.SupplierProductCountResponse;
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
import java.util.Optional;
import java.util.UUID;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;
    private final AppClock appClock;

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

        return tenantProductRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id, 404));
    }

    // =========================================================
    // WRITE (orquestração multi-repo => TenantSchemaUnitOfWork)
    // =========================================================

    public Product create(Product product) {
        validateProduct(product);

        String tenantSchema = requireBoundTenantSchema();

        // ✅ orquestração + multi-repo dentro de um boundary explícito
        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            resolveCategoryAndSubcategory(product);
            resolveSupplier(product);
            validateSubcategoryBelongsToCategory(product);
            return tenantProductRepository.save(product);
        });
    }

    public Product update(UUID id, Product productDetails) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        if (productDetails == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Product existingProduct = tenantProductRepository.findById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado com ID: " + id, 404));

            if (StringUtils.hasText(productDetails.getName())) {
                existingProduct.setName(productDetails.getName().trim());
            }

            if (productDetails.getDescription() != null) {
                existingProduct.setDescription(productDetails.getDescription());
            }

            if (StringUtils.hasText(productDetails.getSku())) {
                String sku = productDetails.getSku().trim();
                Optional<Product> productWithSku = tenantProductRepository.findBySku(sku);
                if (productWithSku.isPresent() && !productWithSku.get().getId().equals(id)) {
                    throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS,
                            "SKU já cadastrado: " + sku, 409);
                }
                existingProduct.setSku(sku);
            }

            if (productDetails.getPrice() != null) {
                validatePrice(productDetails.getPrice());
                existingProduct.setPrice(productDetails.getPrice());
            }

            if (productDetails.getStockQuantity() != null) {
                existingProduct.setStockQuantity(productDetails.getStockQuantity());
            }

            // ✅ category
            if (productDetails.getCategory() != null && productDetails.getCategory().getId() != null) {
                Category category = tenantCategoryRepository.findById(productDetails.getCategory().getId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
                existingProduct.setCategory(category);
            }

            // ✅ subcategory: só mexe se veio no payload
            if (productDetails.getSubcategory() != null) {
                if (productDetails.getSubcategory().getId() != null) {
                    Subcategory sub = tenantSubcategoryRepository
                            .findByIdWithCategory(productDetails.getSubcategory().getId())
                            .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", 404));
                    existingProduct.setSubcategory(sub);
                } else {
                    // veio "subcategory": {} (ou sem id) => limpa
                    existingProduct.setSubcategory(null);
                }
            }

            // ✅ supplier
            if (productDetails.getSupplier() != null && productDetails.getSupplier().getId() != null) {
                Supplier supplier = tenantSupplierRepository.findById(productDetails.getSupplier().getId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado", 404));
                existingProduct.setSupplier(supplier);
            }

            validateSubcategoryBelongsToCategory(existingProduct);

            return tenantProductRepository.save(existingProduct);
        });
    }

    private String requireBoundTenantSchema() {
        String tenantSchema = TenantContext.getOrNull();
        if (tenantSchema == null) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "TenantContext não está bindado (tenantSchema=null). Operação requer contexto TENANT.", 500);
        }
        return tenantSchema;
    }

    // =========================================================
    // Outros writes (mantém como estava)
    // =========================================================

    @TenantTx
    public Product updateStock(UUID id, Integer quantityChange) {
        Product product = findById(id);
        if (quantityChange > 0) product.addToStock(quantityChange);
        else if (quantityChange < 0) product.removeFromStock(Math.abs(quantityChange));
        return tenantProductRepository.save(product);
    }

    @TenantTx
    public Product updatePrice(UUID id, BigDecimal newPrice) {
        validatePrice(newPrice);
        Product product = findById(id);
        product.updatePrice(newPrice);
        return tenantProductRepository.save(product);
    }

    @TenantTx
    public void delete(UUID id) {
        Product product = findById(id);
        product.softDelete(appClock.instant());
        tenantProductRepository.save(product);
    }

    @TenantTx
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        Product product = findById(id);
        product.updateCostPrice(costPrice);
        return tenantProductRepository.save(product);
    }

    @TenantTx
    public Product toggleActive(UUID id) {
        Product product = findById(id);

        if (Boolean.TRUE.equals(product.getDeleted())) {
            throw new ApiException(ApiErrorCode.PRODUCT_DELETED, "Não é permitido alterar produto deletado", 409);
        }

        boolean next = !Boolean.TRUE.equals(product.getActive());
        product.setActive(next);

        return tenantProductRepository.save(product);
    }

    // =========================================================
    // READ helpers / queries
    // =========================================================

    @TenantReadOnlyTx
    public List<Product> searchProducts(String name, BigDecimal minPrice,
                                        BigDecimal maxPrice, Integer minStock, Integer maxStock) {
        return tenantProductRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }

    @TenantReadOnlyTx
    public List<Product> findLowStock(Integer threshold) {
        return tenantProductRepository.findByStockQuantityLessThan(threshold);
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
        // ✅ category obrigatória
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        Category category = tenantCategoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
        product.setCategory(category);

        // ✅ subcategory opcional
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

        if (product.getSubcategory().getCategory() == null
                || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria sem categoria associada (cadastro inconsistente)", 409);
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria não pertence à categoria informada", 409);
        }
    }

    private void validateProduct(Product product) {
        if (product == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        if (!StringUtils.hasText(product.getName())) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "Nome do produto é obrigatório", 400);
        }
        product.setName(product.getName().trim());

        if (product.getPrice() == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_PRICE_REQUIRED, "Preço do produto é obrigatório", 400);
        }
        validatePrice(product.getPrice());

        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getStockQuantity() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_STOCK, "Quantidade em estoque não pode ser negativa", 400);
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null) throw new ApiException(ApiErrorCode.INVALID_PRICE, "Preço não pode ser nulo", 400);
        if (price.compareTo(BigDecimal.ZERO) < 0) throw new ApiException(ApiErrorCode.INVALID_PRICE, "Preço não pode ser negativo", 400);
        if (price.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw new ApiException(ApiErrorCode.PRICE_TOO_HIGH, "Preço muito alto. Valor máximo permitido: 1.000.000", 400);
        }
    }

    @TenantReadOnlyTx
    public List<Product> findByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "categoryId é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedByCategoryId(categoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findByBrand(String brand) {
        if (!StringUtils.hasText(brand)) {
            throw new ApiException(ApiErrorCode.INVALID_BRAND, "brand é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedByBrandIgnoreCase(brand.trim());
    }

    @TenantReadOnlyTx
    public List<Product> findActiveProducts() {
        return tenantProductRepository.findByActiveTrueAndDeletedFalse();
    }

    @TenantReadOnlyTx
    public BigDecimal calculateTotalInventoryValue() {
        return tenantProductRepository.calculateTotalInventoryValue();
    }

    @TenantReadOnlyTx
    public Long countLowStockProducts(Integer threshold) {
        return tenantProductRepository.countLowStock(threshold);
    }

    @TenantReadOnlyTx
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_REQUIRED, "subcategoryId é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedBySubcategoryId(subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findByCategoryAndOptionalSubcategory(Long categoryId, Long subcategoryId) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "categoryId é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedByCategoryAndOptionalSubcategory(categoryId, subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.INVALID_NAME, "name é obrigatório", 400);
        }
        return tenantProductRepository.findByNameContainingIgnoreCase(name.trim());
    }

    @TenantReadOnlyTx
    public Page<Product> findByNamePaged(String name, Pageable pageable) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.INVALID_NAME, "name é obrigatório", 400);
        }
        return tenantProductRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
    }

    @TenantReadOnlyTx
    public List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null || maxPrice == null) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE_RANGE, "minPrice e maxPrice são obrigatórios", 400);
        }
        if (maxPrice.compareTo(minPrice) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE_RANGE, "maxPrice deve ser >= minPrice", 400);
        }
        return tenantProductRepository.findByPriceBetween(minPrice, maxPrice);
    }

    @TenantReadOnlyTx
    public List<Product> findBySupplierId(UUID supplierId) {
        if (supplierId == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "supplierId é obrigatório", 400);
        }
        return tenantProductRepository.findBySupplier_Id(supplierId);
    }

    @TenantReadOnlyTx
    public List<Product> findByNameAndPriceAndStock(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.INVALID_NAME, "name é obrigatório", 400);
        }
        if (minPrice == null || maxPrice == null) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE_RANGE, "minPrice e maxPrice são obrigatórios", 400);
        }
        if (maxPrice.compareTo(minPrice) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE_RANGE, "maxPrice deve ser >= minPrice", 400);
        }
        if (minStock == null || maxStock == null) {
            throw new ApiException(ApiErrorCode.INVALID_STOCK_RANGE, "minStock e maxStock são obrigatórios", 400);
        }
        if (maxStock < minStock) {
            throw new ApiException(ApiErrorCode.INVALID_STOCK_RANGE, "maxStock deve ser >= minStock", 400);
        }

        return tenantProductRepository.findByNameContainingIgnoreCaseAndPriceBetweenAndStockQuantityBetween(
                name.trim(), minPrice, maxPrice, minStock, maxStock
        );
    }

    @TenantReadOnlyTx
    public List<SupplierProductCountResponse> countProductsBySupplier() {
        List<Object[]> rows = tenantProductRepository.countProductsBySupplier();

        return rows.stream()
                .map(row -> new SupplierProductCountResponse(
                        (UUID) row[0],
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    @TenantReadOnlyTx
    public List<Product> findByCategoryId(Long categoryId, boolean includeDeleted, boolean includeInactive) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "categoryId é obrigatório", 400);
        }
        return tenantProductRepository.findByCategoryWithFlags(categoryId, includeDeleted, includeInactive);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByCategoryId(Long categoryId) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        return tenantProductRepository.findByCategory_Id(categoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        return tenantProductRepository.findBySubcategory_Id(subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        if (!StringUtils.hasText(brand)) throw new ApiException(ApiErrorCode.BRAND_REQUIRED, "brand é obrigatório", 400);
        return tenantProductRepository.findAnyByBrandIgnoreCase(brand);
    }
}
