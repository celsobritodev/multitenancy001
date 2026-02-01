// ===============================
// ProductService.java
// (corrigido: resolveCategoryAndSubcategory sem duplicação,
//  update limpando subcategory quando vier null,
//  usando findByIdWithCategory pra validar)
// ===============================
package brito.com.multitenancy001.tenant.products.app;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;
    private final AppClock appClock;

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return tenantProductRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Product findById(UUID id) {
        return tenantProductRepository.findById(id)
            .orElseThrow(() -> new ApiException("PRODUCT_NOT_FOUND",
                "Produto não encontrado com ID: " + id, 404));
    }

    @Transactional
    public Product create(Product product) {
        validateProduct(product);

        resolveCategoryAndSubcategory(product);
        resolveSupplier(product);
        validateSubcategoryBelongsToCategory(product);

        return tenantProductRepository.save(product);
    }

    @Transactional
    public Product update(UUID id, Product productDetails) {
        Product existingProduct = findById(id);

        if (StringUtils.hasText(productDetails.getName())) {
            existingProduct.setName(productDetails.getName());
        }

        if (productDetails.getDescription() != null) {
            existingProduct.setDescription(productDetails.getDescription());
        }

        if (StringUtils.hasText(productDetails.getSku())) {
            Optional<Product> productWithSku = tenantProductRepository.findBySku(productDetails.getSku());
            if (productWithSku.isPresent() && !productWithSku.get().getId().equals(id)) {
                throw new ApiException("SKU_ALREADY_EXISTS",
                    "SKU já cadastrado: " + productDetails.getSku(), 409);
            }
            existingProduct.setSku(productDetails.getSku());
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
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada", 404));
            existingProduct.setCategory(category);
        }

     // ✅ subcategory: só mexe se veio no payload
        if (productDetails.getSubcategory() != null) {
            if (productDetails.getSubcategory().getId() != null) {
                Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(productDetails.getSubcategory().getId())
                        .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada", 404));
                existingProduct.setSubcategory(sub);
            } else {
                // veio "subcategory": {} (ou sem id) => limpa
                existingProduct.setSubcategory(null);
            }
        }



        // ✅ supplier
        if (productDetails.getSupplier() != null && productDetails.getSupplier().getId() != null) {
            Supplier supplier = tenantSupplierRepository.findById(productDetails.getSupplier().getId())
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND", "Fornecedor não encontrado", 404));
            existingProduct.setSupplier(supplier);
        }

        validateSubcategoryBelongsToCategory(existingProduct);

        return tenantProductRepository.save(existingProduct);
    }

    private void resolveSupplier(Product product) {
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            UUID supplierId = product.getSupplier().getId();
            Supplier supplier = tenantSupplierRepository.findById(product.getSupplier().getId())
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                    "Fornecedor não encontrado com ID: " + supplierId, 404));
            product.setSupplier(supplier);
        }
    }


    private void resolveCategoryAndSubcategory(Product product) {
        // ✅ category obrigatória
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException("CATEGORY_REQUIRED", "Categoria é obrigatória", 400);
        }

        Category category = tenantCategoryRepository.findById(product.getCategory().getId())
            .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada", 404));
        product.setCategory(category);

        // ✅ subcategory opcional
        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(product.getSubcategory().getId())
                .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada", 404));
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }
    }

    private void validateSubcategoryBelongsToCategory(Product product) {
        if (product.getSubcategory() == null) return;

        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException("CATEGORY_REQUIRED", "Categoria é obrigatória", 400);
        }

        if (product.getSubcategory().getCategory() == null
            || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException("INVALID_SUBCATEGORY",
                "Subcategoria sem categoria associada (cadastro inconsistente)", 409);
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException("INVALID_SUBCATEGORY",
                "Subcategoria não pertence à categoria informada", 409);
        }
    }

    // ======= outros métodos =======

    @Transactional(readOnly = true)
    public List<Product> searchProducts(String name, BigDecimal minPrice,
                                        BigDecimal maxPrice, Integer minStock, Integer maxStock) {
        return tenantProductRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }


    @Transactional(readOnly = true)
    public List<Product> findLowStock(Integer threshold) {
        return tenantProductRepository.findByStockQuantityLessThan(threshold);
    }

    @Transactional
    public Product updateStock(UUID id, Integer quantityChange) {
        Product product = findById(id);
        if (quantityChange > 0) product.addToStock(quantityChange);
        else if (quantityChange < 0) product.removeFromStock(Math.abs(quantityChange));
        return tenantProductRepository.save(product);
    }

    @Transactional
    public Product updatePrice(UUID id, BigDecimal newPrice) {
        validatePrice(newPrice);
        Product product = findById(id);
        product.updatePrice(newPrice);
        return tenantProductRepository.save(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findById(id);
        product.softDelete(appClock.now());
        tenantProductRepository.save(product);
    }

    private void validateProduct(Product product) {
        if (!StringUtils.hasText(product.getName())) {
            throw new ApiException("PRODUCT_NAME_REQUIRED", "Nome do produto é obrigatório", 400);
        }
        if (product.getPrice() == null) {
            throw new ApiException("PRODUCT_PRICE_REQUIRED", "Preço do produto é obrigatório", 400);
        }
        validatePrice(product.getPrice());

        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getStockQuantity() < 0) {
            throw new ApiException("INVALID_STOCK", "Quantidade em estoque não pode ser negativa", 400);
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null) throw new ApiException("INVALID_PRICE", "Preço não pode ser nulo", 400);
        if (price.compareTo(BigDecimal.ZERO) < 0) throw new ApiException("INVALID_PRICE", "Preço não pode ser negativo", 400);
        if (price.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw new ApiException("PRICE_TOO_HIGH", "Preço muito alto. Valor máximo permitido: 1.000.000", 400);
        }
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException("CATEGORY_REQUIRED", "categoryId é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedByCategoryId(categoryId);
    }


    @Transactional(readOnly = true)
    public List<Product> findByBrand(String brand) {
        if (!StringUtils.hasText(brand)) {
            throw new ApiException("INVALID_BRAND", "brand é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedByBrandIgnoreCase(brand.trim());
    }



    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        return tenantProductRepository.findByActiveTrueAndDeletedFalse();
    }


    @Transactional
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        Product product = findById(id);
        product.updateCostPrice(costPrice);
        return tenantProductRepository.save(product);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalInventoryValue() {
        return tenantProductRepository.calculateTotalInventoryValue();
    }

    @Transactional(readOnly = true)
    public Long countLowStockProducts(Integer threshold) {
        return tenantProductRepository.countLowStock(threshold);
    }
    
 // =========================================================
 // ✅ QUERIES PARA "DAR USO" AOS MÉTODOS DO ProductRepository
 // =========================================================

    @Transactional(readOnly = true)
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) {
            throw new ApiException("SUBCATEGORY_REQUIRED", "subcategoryId é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedBySubcategoryId(subcategoryId);
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategoryAndOptionalSubcategory(Long categoryId, Long subcategoryId) {
        if (categoryId == null) {
            throw new ApiException("CATEGORY_REQUIRED", "categoryId é obrigatório", 400);
        }
        return tenantProductRepository.findActiveNotDeletedByCategoryAndOptionalSubcategory(categoryId, subcategoryId);
    }


 @Transactional(readOnly = true)
 public List<Product> findByName(String name) {
     if (!StringUtils.hasText(name)) {
         throw new ApiException("INVALID_NAME", "name é obrigatório", 400);
     }
     return tenantProductRepository.findByNameContainingIgnoreCase(name.trim());
 }

 @Transactional(readOnly = true)
 public Page<Product> findByNamePaged(String name, Pageable pageable) {
     if (!StringUtils.hasText(name)) {
         throw new ApiException("INVALID_NAME", "name é obrigatório", 400);
     }
     return tenantProductRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
 }

 @Transactional(readOnly = true)
 public List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
     if (minPrice == null || maxPrice == null) {
         throw new ApiException("INVALID_PRICE_RANGE", "minPrice e maxPrice são obrigatórios", 400);
     }
     if (maxPrice.compareTo(minPrice) < 0) {
         throw new ApiException("INVALID_PRICE_RANGE", "maxPrice deve ser >= minPrice", 400);
     }
     return tenantProductRepository.findByPriceBetween(minPrice, maxPrice);
 }

 @Transactional(readOnly = true)
 public List<Product> findBySupplierId(UUID supplierId) {
     if (supplierId == null) {
         throw new ApiException("SUPPLIER_REQUIRED", "supplierId é obrigatório", 400);
     }
     return tenantProductRepository.findBySupplier_Id(supplierId);
 }

 @Transactional(readOnly = true)
 public List<Product> findByNameAndPriceAndStock(
         String name,
         BigDecimal minPrice,
         BigDecimal maxPrice,
         Integer minStock,
         Integer maxStock
 ) {
     if (!StringUtils.hasText(name)) {
         throw new ApiException("INVALID_NAME", "name é obrigatório", 400);
     }
     if (minPrice == null || maxPrice == null) {
         throw new ApiException("INVALID_PRICE_RANGE", "minPrice e maxPrice são obrigatórios", 400);
     }
     if (maxPrice.compareTo(minPrice) < 0) {
         throw new ApiException("INVALID_PRICE_RANGE", "maxPrice deve ser >= minPrice", 400);
     }
     if (minStock == null || maxStock == null) {
         throw new ApiException("INVALID_STOCK_RANGE", "minStock e maxStock são obrigatórios", 400);
     }
     if (maxStock < minStock) {
         throw new ApiException("INVALID_STOCK_RANGE", "maxStock deve ser >= minStock", 400);
     }

     return tenantProductRepository.findByNameContainingIgnoreCaseAndPriceBetweenAndStockQuantityBetween(
             name.trim(), minPrice, maxPrice, minStock, maxStock
     );
 }

 /**
  * Usa o @Query:
  * SELECT p.supplier.id, COUNT(p) FROM Product p GROUP BY p.supplier.id
  */
 @Transactional(readOnly = true)
 public List<SupplierProductCountResponse> countProductsBySupplier() {
     List<Object[]> rows = tenantProductRepository.countProductsBySupplier();

     return rows.stream()
             .map(row -> new SupplierProductCountResponse(
                     (UUID) row[0],
                     ((Number) row[1]).longValue()
             ))
             .toList();
 }

 
 @Transactional
 public Product toggleActive(UUID id) {
     Product product = findById(id);

     if (Boolean.TRUE.equals(product.getDeleted())) {
         throw new ApiException("PRODUCT_DELETED", "Não é permitido alterar produto deletado", 409);
     }

     boolean next = !Boolean.TRUE.equals(product.getActive());
     product.setActive(next);

     return tenantProductRepository.save(product);
 }
 
 @Transactional(readOnly = true)
 public List<Product> findByCategoryId(Long categoryId, boolean includeDeleted, boolean includeInactive) {
     if (categoryId == null) {
         throw new ApiException("CATEGORY_REQUIRED", "categoryId é obrigatório", 400);
     }
     return tenantProductRepository.findByCategoryWithFlags(categoryId, includeDeleted, includeInactive);
 }

 @Transactional(readOnly = true)
 public List<Product> findAnyByCategoryId(Long categoryId) {
     if (categoryId == null) throw new ApiException("CATEGORY_ID_REQUIRED", "categoryId é obrigatório", 400);
     return tenantProductRepository.findByCategory_Id(categoryId);
 }

 @Transactional(readOnly = true)
 public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
     if (subcategoryId == null) throw new ApiException("SUBCATEGORY_ID_REQUIRED", "subcategoryId é obrigatório", 400);
     return tenantProductRepository.findBySubcategory_Id(subcategoryId);
 }

 @Transactional(readOnly = true)
 public List<Product> findAnyByBrandIgnoreCase(String brand) {
     if (!StringUtils.hasText(brand)) throw new ApiException("BRAND_REQUIRED", "brand é obrigatório", 400);
     return tenantProductRepository.findAnyByBrandIgnoreCase(brand);
 }

 
    
}
