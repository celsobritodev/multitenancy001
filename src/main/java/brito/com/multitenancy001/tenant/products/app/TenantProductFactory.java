package brito.com.multitenancy001.tenant.products.app;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory de produtos para o write path.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Construir a entidade inicial de {@link Product} a partir do command de criação.</li>
 *   <li>Popular referências leves de categoria, subcategoria e fornecedor.</li>
 *   <li>Aplicar defaults simples de estado inicial da entidade.</li>
 * </ul>
 *
 * <p>Esta classe não valida domínio profundo nem resolve relações completas em repositório.</p>
 */
@Component
@Slf4j
public class TenantProductFactory {

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

        log.debug(
                "PRODUCT_FACTORY_CREATED | sku={} | categoryId={} | subcategoryId={} | supplierId={}",
                product.getSku(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getSubcategory() != null ? product.getSubcategory().getId() : null,
                product.getSupplier() != null ? product.getSupplier().getId() : null
        );

        return product;
    }
}