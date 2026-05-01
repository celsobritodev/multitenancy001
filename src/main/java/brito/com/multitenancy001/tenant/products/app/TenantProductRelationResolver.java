package brito.com.multitenancy001.tenant.products.app;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolver de relações do write path de produtos.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Recarregar produto com relações.</li>
 *   <li>Resolver categoria/subcategoria completas.</li>
 *   <li>Resolver fornecedor completo.</li>
 *   <li>Validar coerência entre categoria e subcategoria.</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded</li>
 *   <li>Sem alteração de comportamento</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductRelationResolver {

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
                        "Produto não encontrado após " + operation + " (ID: " + productId + ")"
                ));
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
                            "Fornecedor não encontrado com ID: " + supplierId
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
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória");
        }

        Long categoryId = product.getCategory().getId();

        log.info("Resolvendo categoria do produto. categoryId={}", categoryId);

        Category category = tenantCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.CATEGORY_NOT_FOUND,
                        "Categoria não encontrada"
                ));
        product.setCategory(category);

        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Long subcategoryId = product.getSubcategory().getId();

            log.info("Resolvendo subcategoria do produto. subcategoryId={}", subcategoryId);

            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(subcategoryId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.SUBCATEGORY_NOT_FOUND,
                            "Subcategoria não encontrada"
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
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória");
        }

        if (product.getSubcategory().getCategory() == null || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria sem categoria associada (cadastro inconsistente)"
            );
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria não pertence à categoria informada"
            );
        }
    }
}