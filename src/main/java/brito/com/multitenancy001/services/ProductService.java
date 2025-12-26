package brito.com.multitenancy001.services;

import brito.com.multitenancy001.entities.tenant.Category;
import brito.com.multitenancy001.entities.tenant.Product;
import brito.com.multitenancy001.entities.tenant.Subcategory;
import brito.com.multitenancy001.entities.tenant.Supplier;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.product.CategoryRepository;
import brito.com.multitenancy001.repositories.product.ProductRepository;
import brito.com.multitenancy001.repositories.product.SubcategoryRepository;
import brito.com.multitenancy001.repositories.supplier.SupplierRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ApiException("PRODUCT_NOT_FOUND",
                        "Produto não encontrado com ID: " + id, 404));
    }

    @Transactional
    public Product create(Product product) {
        validateProduct(product);

        // ✅ resolve categoria/subcategoria
        resolveCategoryAndSubcategory(product);

        // ✅ resolve supplier
        resolveSupplier(product);

        // ✅ valida vínculo subcategoria -> categoria
        validateSubcategoryBelongsToCategory(product);

        return productRepository.save(product);
    }

    @Transactional
    public Product update(String id, Product productDetails) {
        Product existingProduct = findById(id);

        if (StringUtils.hasText(productDetails.getName())) {
            existingProduct.setName(productDetails.getName());
        }

        if (productDetails.getDescription() != null) {
            existingProduct.setDescription(productDetails.getDescription());
        }

        if (StringUtils.hasText(productDetails.getSku())) {
            Optional<Product> productWithSku = productRepository.findBySku(productDetails.getSku());
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

        // ✅ categoria/subcategoria (se vier no payload)
        if (productDetails.getCategory() != null && productDetails.getCategory().getId() != null) {
            Category category = categoryRepository.findById(productDetails.getCategory().getId())
                    .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND",
                            "Categoria não encontrada", 404));
            existingProduct.setCategory(category);
        }

        if (productDetails.getSubcategory() != null && productDetails.getSubcategory().getId() != null) {
        	Subcategory sub = subcategoryRepository.findByIdWithCategory(productDetails.getSubcategory().getId())
        	        .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND",
        	                "Subcategoria não encontrada", 404));
        	existingProduct.setSubcategory(sub);

        }

     //productDetails.getSubcategory() == nul


        // ✅ supplier
        if (productDetails.getSupplier() != null && productDetails.getSupplier().getId() != null) {
            Supplier supplier = supplierRepository.findById(productDetails.getSupplier().getId())
                    .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                            "Fornecedor não encontrado", 404));
            existingProduct.setSupplier(supplier);
        }

        validateSubcategoryBelongsToCategory(existingProduct);

        return productRepository.save(existingProduct);
    }

    private void resolveSupplier(Product product) {
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            String supplierId = product.getSupplier().getId();
            Supplier supplier = supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                            "Fornecedor não encontrado com ID: " + supplierId, 404));
            product.setSupplier(supplier);
        }
    }

    private void resolveCategoryAndSubcategory(Product product) {

        // ✅ CATEGORY é obrigatória
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException("CATEGORY_REQUIRED", "Categoria é obrigatória", 400);
        }

        Category category = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND",
                        "Categoria não encontrada", 404));
        product.setCategory(category);

        // ✅ SUBCATEGORY é opcional
        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Subcategory sub = subcategoryRepository.findByIdWithCategory(product.getSubcategory().getId())
                    .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND",
                            "Subcategoria não encontrada", 404));
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }
    }


        
        
        
        

        // subcategory opcional
        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Subcategory sub = subcategoryRepository.findById(product.getSubcategory().getId())
                    .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND",
                            "Subcategoria não encontrada", 404));
            product.setSubcategory(sub);
        }
    }

    private void validateSubcategoryBelongsToCategory(Product product) {
        if (product.getSubcategory() == null) {
            return; // subcategoria é opcional
        }

        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException("CATEGORY_REQUIRED", "Categoria é obrigatória", 400);
        }

        if (product.getSubcategory().getCategory() == null
                || product.getSubcategory().getCategory().getId() == null) {
            // por segurança (caso subcategory não venha com category preenchida)
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

    
    
    // ======= métodos seus mantidos / ajustados =======

    @Transactional(readOnly = true)
    public List<Product> searchProducts(String name, String sku, BigDecimal minPrice,
                                        BigDecimal maxPrice, Integer minStock, Integer maxStock) {
        return productRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }

    @Transactional(readOnly = true)
    public List<Product> findLowStock(Integer threshold) {
        return productRepository.findByStockQuantityLessThan(threshold);
    }

    @Transactional
    public Product updateStock(String id, Integer quantityChange) {
        Product product = findById(id);
        if (quantityChange > 0) product.addToStock(quantityChange);
        else if (quantityChange < 0) product.removeFromStock(Math.abs(quantityChange));
        return productRepository.save(product);
    }

    @Transactional
    public Product updatePrice(String id, BigDecimal newPrice) {
        validatePrice(newPrice);
        Product product = findById(id);
        product.updatePrice(newPrice);
        return productRepository.save(product);
    }

    @Transactional
    public void delete(String id) {
        Product product = findById(id);
        product.softDelete();
        productRepository.save(product);
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
        return productRepository.findByCategory_Id(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Product> findByBrand(String brand) {
        // opcional: criar repo findByBrandIgnoreCase para não filtrar em memória
        return productRepository.findAll().stream()
                .filter(p -> brand.equals(p.getBrand()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        return productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()) && !Boolean.TRUE.equals(p.getDeleted()))
                .toList();
    }

    @Transactional
    public Product updateCostPrice(String id, BigDecimal costPrice) {
        Product product = findById(id);
        product.updateCostPrice(costPrice);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalInventoryValue() {
        return productRepository.calculateTotalInventoryValue();
    }

    @Transactional(readOnly = true)
    public Long countLowStockProducts(Integer threshold) {
        return productRepository.countLowStock(threshold);
    }
}
