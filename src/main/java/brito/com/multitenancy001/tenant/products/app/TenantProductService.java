package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
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

@Service
@RequiredArgsConstructor
public class TenantProductService {

    private final TxExecutor txExecutor;

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;

    // =========================================================
    // READ
    // =========================================================

    public Product findById(UUID id) {
        requireId(id);
        return txExecutor.tenantReadOnlyTx(() ->
                tenantProductRepository.findById(id)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "Produto não encontrado"))
        );
    }

    public List<Product> findActiveProducts() {
        return txExecutor.tenantReadOnlyTx(tenantProductRepository::findByActiveTrueAndDeletedFalse);
    }

    public List<Product> findByCategoryId(Long categoryId) {
        requireCategoryId(categoryId);
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findActiveNotDeletedByCategoryId(categoryId));
    }

    public List<Product> findByCategoryId(Long categoryId, Boolean includeDeleted, Boolean includeInactive) {
        requireCategoryId(categoryId);
        boolean incDel = Boolean.TRUE.equals(includeDeleted);
        boolean incIna = Boolean.TRUE.equals(includeInactive);

        return txExecutor.tenantReadOnlyTx(() ->
                tenantProductRepository.findByCategoryWithFlags(categoryId, incDel, incIna)
        );
    }

    public List<Product> findByCategoryAndOptionalSubcategory(Long categoryId, Long subcategoryId) {
        requireCategoryId(categoryId);
        return txExecutor.tenantReadOnlyTx(() ->
                tenantProductRepository.findActiveNotDeletedByCategoryAndOptionalSubcategory(categoryId, subcategoryId)
        );
    }

    public List<Product> findBySubcategoryId(Long subcategoryId) {
        requireSubcategoryId(subcategoryId);
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findActiveNotDeletedBySubcategoryId(subcategoryId));
    }

    public List<Product> findByBrand(String brand) {
        if (!StringUtils.hasText(brand)) return List.of();
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findActiveNotDeletedByBrandIgnoreCase(brand));
    }

    public List<Product> findByName(String name) {
        if (!StringUtils.hasText(name)) return List.of();
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findByNameContainingIgnoreCase(name));
    }

    public Page<Product> findByNamePaged(String name, Pageable pageable) {
        if (!StringUtils.hasText(name)) {
            return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findAll(pageable));
        }
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findByNameContainingIgnoreCase(name, pageable));
    }

    public List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null || maxPrice == null) return List.of();
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findByPriceBetween(minPrice, maxPrice));
    }

    public List<Product> findBySupplierId(UUID supplierId) {
        requireSupplierId(supplierId);
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findBySupplier_Id(supplierId));
    }

    public List<Product> findByNameAndPriceAndStock(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        if (!StringUtils.hasText(name)) name = "";
        if (minPrice == null) minPrice = BigDecimal.ZERO;
        if (maxPrice == null) maxPrice = new BigDecimal("999999999");
        if (minStock == null) minStock = 0;
        if (maxStock == null) maxStock = Integer.MAX_VALUE;

        final String finalName = name;
        final BigDecimal finalMinPrice = minPrice;
        final BigDecimal finalMaxPrice = maxPrice;
        final Integer finalMinStock = minStock;
        final Integer finalMaxStock = maxStock;

        return txExecutor.tenantReadOnlyTx(() ->
                tenantProductRepository.findByNameContainingIgnoreCaseAndPriceBetweenAndStockQuantityBetween(
                        finalName, finalMinPrice, finalMaxPrice, finalMinStock, finalMaxStock
                )
        );
    }

    public List<SupplierProductCountResponse> countProductsBySupplier() {
        return txExecutor.tenantReadOnlyTx(() ->
                tenantProductRepository.countProductsBySupplier()
                        .stream()
                        .map(arr -> new SupplierProductCountResponse((UUID) arr[0], ((Number) arr[1]).longValue()))
                        .toList()
        );
    }

    public BigDecimal calculateTotalInventoryValue() {
        return txExecutor.tenantReadOnlyTx(() -> {
            BigDecimal v = tenantProductRepository.calculateTotalInventoryValue();
            return v == null ? BigDecimal.ZERO : v;
        });
    }

    public Long countLowStockProducts(Integer threshold) {
        int t = threshold == null ? 0 : threshold;
        return txExecutor.tenantReadOnlyTx(() ->
                (long) tenantProductRepository.findByStockQuantityLessThan(t).size()
        );
    }

    public List<Product> findAnyByCategoryId(Long categoryId) {
        requireCategoryId(categoryId);
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findByCategory_Id(categoryId));
    }

    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        requireSubcategoryId(subcategoryId);
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findBySubcategory_Id(subcategoryId));
    }

    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        if (!StringUtils.hasText(brand)) return List.of();
        return txExecutor.tenantReadOnlyTx(() -> tenantProductRepository.findAnyByBrandIgnoreCase(brand));
    }

    // =========================================================
    // WRITE
    // =========================================================

    public Product create(Product product) {
        if (product == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "Produto é obrigatório");
        if (!StringUtils.hasText(product.getName())) throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "Nome é obrigatório");
        if (product.getPrice() == null) throw new ApiException(ApiErrorCode.PRODUCT_PRICE_REQUIRED, "Preço é obrigatório");

        return txExecutor.tenantTx(() -> {
            normalizeAndAttachRelations(product);
            product.setId(null); // garantir create
            return tenantProductRepository.save(product);
        });
    }

    public Product toggleActive(UUID id) {
        requireId(id);
        return txExecutor.tenantTx(() -> {
            Product p = tenantProductRepository.findById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "Produto não encontrado"));
            if (Boolean.TRUE.equals(p.getDeleted())) {
                throw new ApiException(ApiErrorCode.PRODUCT_DELETED, "Produto deletado");
            }
            p.setActive(!Boolean.TRUE.equals(p.getActive()));
            return tenantProductRepository.save(p);
        });
    }

    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        requireId(id);
        return txExecutor.tenantTx(() -> {
            Product p = tenantProductRepository.findById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "Produto não encontrado"));
            if (Boolean.TRUE.equals(p.getDeleted())) {
                throw new ApiException(ApiErrorCode.PRODUCT_DELETED, "Produto deletado");
            }
            p.setCostPrice(costPrice);
            return tenantProductRepository.save(p);
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void normalizeAndAttachRelations(Product product) {
        // supplier
        Supplier supplier = null;
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            UUID supplierId = product.getSupplier().getId();
            supplier = tenantSupplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado"));
            product.setSupplier(supplier);
        }

        // category (obrigatória na prática do seu controller)
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "Categoria é obrigatória");
        }
        Long categoryId = product.getCategory().getId();
        Category category = tenantCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada"));
        product.setCategory(category);

        // subcategory (opcional)
        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Long subId = product.getSubcategory().getId();
            Subcategory sub = tenantSubcategoryRepository.findById(subId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada"));

            // valida subcategoria pertence à categoria
            if (sub.getCategory() == null || sub.getCategory().getId() == null || !Objects.equals(sub.getCategory().getId(), categoryId)) {
                throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY, "Subcategoria não pertence à categoria informada");
            }

            product.setSubcategory(sub);
        }
    }

    private void requireId(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "ProductId obrigatório");
    }

    private void requireCategoryId(Long id) {
        if (id == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "CategoryId obrigatório");
    }

    private void requireSubcategoryId(Long id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "SubcategoryId obrigatório");
    }

    private void requireSupplierId(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "SupplierId obrigatório");
    }
}
