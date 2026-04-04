package brito.com.multitenancy001.tenant.products.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplicador de mutações de update de produto.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Aplicar alterações permitidas no aggregate {@link Product}.</li>
 *   <li>Resolver dependências relacionais afetadas pelo update.</li>
 *   <li>Validar unicidade de SKU durante a mutação.</li>
 *   <li>Garantir coerência final entre categoria e subcategoria.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductUpdateApplier {

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;
    private final TenantProductValidationService tenantProductValidationService;
    private final TenantProductRelationResolver tenantProductRelationResolver;

    /**
     * Aplica alterações no produto existente.
     *
     * @param existing entidade existente
     * @param cmd command de update
     */
    public void applyUpdates(Product existing, UpdateProductCommand cmd) {
        if (existing == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "produto existente é obrigatório", 400);
        }
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

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
            tenantProductValidationService.validatePrice(cmd.price());
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

        tenantProductRelationResolver.validateSubcategoryBelongsToCategory(existing);

        log.debug(
                "PRODUCT_UPDATE_APPLIED | productId={} | sku={} | active={}",
                existing.getId(),
                existing.getSku(),
                existing.getActive()
        );
    }
}