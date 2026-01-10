package brito.com.multitenancy001.tenant.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.domain.product.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

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

	@Query("SELECT p FROM Product p WHERE "
			+ "(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND "
			+ "(:minPrice IS NULL OR p.price >= :minPrice) AND " + "(:maxPrice IS NULL OR p.price <= :maxPrice) AND "
			+ "(:minStock IS NULL OR p.stockQuantity >= :minStock) AND "
			+ "(:maxStock IS NULL OR p.stockQuantity <= :maxStock)")
	List<Product> searchProducts(@Param("name") String name, @Param("minPrice") BigDecimal minPrice,
			@Param("maxPrice") BigDecimal maxPrice, @Param("minStock") Integer minStock,
			@Param("maxStock") Integer maxStock);

	List<Product> findByNameContainingIgnoreCaseAndPriceBetweenAndStockQuantityBetween(String name, BigDecimal minPrice,
			BigDecimal maxPrice, Integer minStock, Integer maxStock);

	@Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= :threshold")
	Long countLowStock(@Param("threshold") Integer threshold);

	@Query("SELECT SUM(p.stockQuantity * p.price) FROM Product p")
	BigDecimal calculateTotalInventoryValue();

	@Query("SELECT p.supplier.id, COUNT(p) FROM Product p GROUP BY p.supplier.id")
	List<Object[]> countProductsBySupplier();
}