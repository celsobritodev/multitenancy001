package brito.com.multitenancy001.tenant.products.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.products.domain.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantProductRepository extends JpaRepository<Product, UUID> {

    // =========================================================
    // ✅ "CATÁLOGO": SOMENTE ATIVOS E NÃO DELETADOS
    // =========================================================

    @Query("""
        SELECT p FROM Product p
        WHERE p.deleted = false
          AND p.active = true
          AND LOWER(p.brand) = LOWER(:brand)
        """)
    List<Product> findActiveNotDeletedByBrandIgnoreCase(@Param("brand") String brand);

    @Query("""
        SELECT p FROM Product p
        WHERE p.deleted = false
          AND p.active = true
          AND p.category.id = :categoryId
        """)
    List<Product> findActiveNotDeletedByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.deleted = false
          AND p.active = true
          AND p.subcategory.id = :subcategoryId
        """)
    List<Product> findActiveNotDeletedBySubcategoryId(@Param("subcategoryId") Long subcategoryId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.deleted = false
          AND p.active = true
          AND p.category.id = :categoryId
          AND (:subcategoryId IS NULL OR p.subcategory.id = :subcategoryId)
        """)
    List<Product> findActiveNotDeletedByCategoryAndOptionalSubcategory(@Param("categoryId") Long categoryId,
                                                                      @Param("subcategoryId") Long subcategoryId);

    // ✅ seu endpoint /active
    List<Product> findByActiveTrueAndDeletedFalse();

    // =========================================================
    // EXISTENTES (mantidos)
    // =========================================================

   
    List<Product> findByCategory_Id(Long categoryId);

    List<Product> findBySubcategory_Id(Long subcategoryId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.category.id = :categoryId
          AND (:subcategoryId IS NULL OR p.subcategory.id = :subcategoryId)
        """)
    List<Product> findByCategoryAndOptionalSubcategory(@Param("categoryId") Long categoryId,
                                                      @Param("subcategoryId") Long subcategoryId);

    List<Product> findByNameContainingIgnoreCase(String name);

    Optional<Product> findBySku(String sku);

    List<Product> findByStockQuantityLessThan(Integer quantity);

    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    List<Product> findBySupplier_Id(UUID supplierId);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
        SELECT p FROM Product p WHERE
            (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND
            (:minPrice IS NULL OR p.price >= :minPrice) AND
            (:maxPrice IS NULL OR p.price <= :maxPrice) AND
            (:minStock IS NULL OR p.stockQuantity >= :minStock) AND
            (:maxStock IS NULL OR p.stockQuantity <= :maxStock)
        """)
    List<Product> searchProducts(@Param("name") String name,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("minStock") Integer minStock,
                                 @Param("maxStock") Integer maxStock);

    List<Product> findByNameContainingIgnoreCaseAndPriceBetweenAndStockQuantityBetween(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    );

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= :threshold")
    Long countLowStock(@Param("threshold") Integer threshold);

    @Query("SELECT SUM(p.stockQuantity * p.price) FROM Product p")
    BigDecimal calculateTotalInventoryValue();

    @Query("SELECT p.supplier.id, COUNT(p) FROM Product p GROUP BY p.supplier.id")
    List<Object[]> countProductsBySupplier();
    
    @Query("""
    	    SELECT p FROM Product p
    	    WHERE (:includeDeleted = true OR p.deleted = false)
    	      AND (:includeInactive = true OR p.active = true)
    	      AND p.category.id = :categoryId
    	    """)
    	List<Product> findByCategoryWithFlags(@Param("categoryId") Long categoryId,
    	                                     @Param("includeDeleted") boolean includeDeleted,
    	                                     @Param("includeInactive") boolean includeInactive);
   
    
    // =========================================================
    // ANY (admin/relatórios internos) ⚠️ pode incluir deleted/inactive
    // =========================================================

    /**
     * ⚠️ Pode incluir deleted/inactive.
     * Use apenas para telas/admin/relatórios internos.
     * Para catálogo público use findActiveNotDeleted*.
     */
    List<Product> findAnyByBrandIgnoreCase(String brand);


    
    

}
