package brito.com.example.multitenancy001.services;

import brito.com.example.multitenancy001.entities.tenant.Product;
import brito.com.example.multitenancy001.entities.tenant.Supplier;
import brito.com.example.multitenancy001.repositories.ProductRepository;
import brito.com.example.multitenancy001.repositories.SupplierRepository;
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
    
    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com ID: " + id));
    }
    
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
    public Product create(Product product) {
        validateProduct(product);
        
        // Processa supplierId do ProductRequest
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            String supplierId = product.getSupplier().getId();
            Supplier supplier = supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado com ID: " + supplierId));
            product.setSupplier(supplier);
        }
        
        return productRepository.save(product);
    }
    
    @Transactional
    public Product update(String id, Product productDetails) {
        Product existingProduct = findById(id);
        
        // Atualiza campos básicos
        if (StringUtils.hasText(productDetails.getName())) {
            existingProduct.setName(productDetails.getName());
        }
        
        if (productDetails.getDescription() != null) {
            existingProduct.setDescription(productDetails.getDescription());
        }
        
        if (StringUtils.hasText(productDetails.getSku())) {
            // Verifica unicidade do SKU
            Optional<Product> productWithSku = productRepository.findBySku(productDetails.getSku());
            if (productWithSku.isPresent() && !productWithSku.get().getId().equals(id)) {
                throw new RuntimeException("SKU já cadastrado: " + productDetails.getSku());
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
        
        // Atualiza supplier se fornecido
        if (productDetails.getSupplier() != null && productDetails.getSupplier().getId() != null) {
            Supplier supplier = supplierRepository.findById(productDetails.getSupplier().getId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));
            existingProduct.setSupplier(supplier);
        }
        
        return productRepository.save(existingProduct);
    }
    
    @Transactional
    public Product updateStock(String id, Integer quantityChange) {
        Product product = findById(id);
        
        if (quantityChange > 0) {
            product.addToStock(quantityChange);
        } else if (quantityChange < 0) {
            product.removeFromStock(Math.abs(quantityChange));
        }
        
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
    
    @Transactional
    public void deleteMultiple(List<String> ids) {
        for (String id : ids) {
            delete(id);
        }
    }
    
    @Transactional(readOnly = true)
    public long count() {
        return productRepository.count();
    }
    
    @Transactional(readOnly = true)
    public String exportProducts() {
        List<Product> products = productRepository.findAll();
        
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Descrição,SKU,Preço,Estoque,Estoque Mín,Estoque Máx,Fornecedor,Ativo,Criado em,Atualizado em\n");
        
        for (Product product : products) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%d,%s,%s,\"%s\",%s,\"%s\",\"%s\"\n",
                    product.getId(),
                    product.getName(),
                    product.getDescription() != null ? product.getDescription() : "",
                    product.getSku() != null ? product.getSku() : "",
                    product.getPrice(),
                    product.getStockQuantity(),
                    product.getMinStock() != null ? product.getMinStock() : "",
                    product.getMaxStock() != null ? product.getMaxStock() : "",
                    product.getSupplier() != null ? product.getSupplier().getName() : "",
                    product.getActive() ? "Sim" : "Não",
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            ));
        }
        
        return csv.toString();
    }
    
    // Métodos auxiliares
    
    private void validateProduct(Product product) {
        if (!StringUtils.hasText(product.getName())) {
            throw new RuntimeException("Nome do produto é obrigatório");
        }
        
        if (product.getPrice() == null) {
            throw new RuntimeException("Preço do produto é obrigatório");
        }
        
        validatePrice(product.getPrice());
        
        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0);
        }
        
        if (product.getStockQuantity() < 0) {
            throw new RuntimeException("Quantidade em estoque não pode ser negativa");
        }
    }
    
    private void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new RuntimeException("Preço não pode ser nulo");
        }
        
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Preço não pode ser negativo");
        }
        
        if (price.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            throw new RuntimeException("Preço muito alto. Valor máximo permitido: 1.000.000");
        }
    }
    
    // Métodos adicionais para os novos campos
    
    @Transactional(readOnly = true)
    public List<Product> findByCategory(String category) {
        return productRepository.findAll().stream()
                .filter(p -> category.equals(p.getCategory()))
                .toList();
    }
    
    @Transactional(readOnly = true)
    public List<Product> findByBrand(String brand) {
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