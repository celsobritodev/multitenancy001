package brito.com.multitenancy001.tenant.products.app;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
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
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", ApiErrorCode.PRODUCT_ID_REQUIRED.defaultHttpStatus());

        return tenantProductRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "Produto não encontrado com ID: " + id, ApiErrorCode.PRODUCT_NOT_FOUND.defaultHttpStatus()));
    }

    // =========================================================
    // WRITE (orquestração multi-repo => TenantSchemaUnitOfWork)
    // =========================================================

    public Product create(Product product) {
        validateProduct(product);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            resolveCategoryAndSubcategory(product);
            resolveSupplier(product);
            validateSubcategoryBelongsToCategory(product);
            return tenantProductRepository.save(product);
        });
    }

    public Product update(UUID id, Product productDetails) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", ApiErrorCode.PRODUCT_ID_REQUIRED.defaultHttpStatus());
        if (productDetails == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", ApiErrorCode.PRODUCT_REQUIRED.defaultHttpStatus());

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Product existingProduct = tenantProductRepository.findById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "Produto não encontrado com ID: " + id, ApiErrorCode.PRODUCT_NOT_FOUND.defaultHttpStatus()));

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
                    throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + sku, ApiErrorCode.SKU_ALREADY_EXISTS.defaultHttpStatus());
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

            if (productDetails.getCategory() != null && productDetails.getCategory().getId() != null) {
                Category category = tenantCategoryRepository.findById(productDetails.getCategory().getId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", ApiErrorCode.CATEGORY_NOT_FOUND.defaultHttpStatus()));
                existingProduct.setCategory(category);
            }

            if (productDetails.getSubcategory() != null) {
                if (productDetails.getSubcategory().getId() != null) {
                    Subcategory sub = tenantSubcategoryRepository
                            .findByIdWithCategory(productDetails.getSubcategory().getId())
                            .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", ApiErrorCode.SUBCATEGORY_NOT_FOUND.defaultHttpStatus()));
                    existingProduct.setSubcategory(sub);
                } else {
                    existingProduct.setSubcategory(null);
                }
            }

            if (productDetails.getSupplier() != null && productDetails.getSupplier().getId() != null) {
                Supplier supplier = tenantSupplierRepository.findById(productDetails.getSupplier().getId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.DOMAIN_RULE_VIOLATION, "Fornecedor não encontrado", 404));
                existingProduct.setSupplier(supplier);
            }

            validateSubcategoryBelongsToCategory(existingProduct);

            return tenantProductRepository.save(existingProduct);
        });
    }

    private String requireBoundTenantSchema() {
        String tenantSchema = TenantContext.getOrNull();
        if (tenantSchema == null) {
            throw new ApiException(
                    ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "TenantContext não está bindado (tenantSchema=null). Operação requer contexto TENANT.",
                    ApiErrorCode.TENANT_CONTEXT_REQUIRED.defaultHttpStatus()
            );
        }
        return tenantSchema;
    }

    // =========================================================
    // Outros writes
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
            throw new ApiException(ApiErrorCode.PRODUCT_DELETED, "Não é permitido alterar produto deletado", ApiErrorCode.PRODUCT_DELETED.defaultHttpStatus());
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
                    .orElseThrow(() -> new ApiException(ApiErrorCode.DOMAIN_RULE_VIOLATION, "Fornecedor não encontrado com ID: " + supplierId, 404));
            product.setSupplier(supplier);
        }
    }

    private void resolveCategoryAndSubcategory(Product product) {
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", ApiErrorCode.CATEGORY_REQUIRED.defaultHttpStatus());
        }

        Category category = tenantCategoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", ApiErrorCode.CATEGORY_NOT_FOUND.defaultHttpStatus()));
        product.setCategory(category);

        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(product.getSubcategory().getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", ApiErrorCode.SUBCATEGORY_NOT_FOUND.defaultHttpStatus()));
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }
    }

    private void validateSubcategoryBelongsToCategory(Product product) {
        if (product.getSubcategory() == null) return;

        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", ApiErrorCode.CATEGORY_REQUIRED.defaultHttpStatus());
        }

        if (product.getSubcategory().getCategory() == null
                || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY, "Subcategoria sem categoria associada (cadastro inconsistente)", ApiErrorCode.INVALID_SUBCATEGORY.defaultHttpStatus());
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY, "Subcategoria não pertence à categoria informada", ApiErrorCode.INVALID_SUBCATEGORY.defaultHttpStatus());
        }
    }

    private void validateProduct(Product product) {
        if (product == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", ApiErrorCode.PRODUCT_REQUIRED.defaultHttpStatus());

        if (!StringUtils.hasText(product.getName())) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "Nome do produto é obrigatório", ApiErrorCode.PRODUCT_NAME_REQUIRED.defaultHttpStatus());
        }
        product.setName(product.getName().trim());

        if (product.getPrice() == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_PRICE_REQUIRED, "Preço do produto é obrigatório", ApiErrorCode.PRODUCT_PRICE_REQUIRED.defaultHttpStatus());
        }
        validatePrice(product.getPrice());

        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getStockQuantity() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_STOCK, "Quantidade em estoque não pode ser negativa", ApiErrorCode.INVALID_STOCK.defaultHttpStatus());
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null) throw new ApiException(ApiErrorCode.INVALID_PRICE, "Preço não pode ser nulo", ApiErrorCode.INVALID_PRICE.defaultHttpStatus());
        if (price.compareTo(BigDecimal.ZERO) < 0) throw new ApiException(ApiErrorCode.INVALID_PRICE, "Preço não pode ser negativo", ApiErrorCode.INVALID_PRICE.defaultHttpStatus());
        if (price.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw new ApiException(ApiErrorCode.PRICE_TOO_HIGH, "Preço muito alto. Valor máximo permitido: 1.000.000", ApiErrorCode.PRICE_TOO_HIGH.defaultHttpStatus());
        }
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
}
