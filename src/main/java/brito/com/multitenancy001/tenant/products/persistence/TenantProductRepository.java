package brito.com.multitenancy001.tenant.products.persistence;

import brito.com.multitenancy001.tenant.products.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA (Tenant): Products.
 *
 * Objetivo:
 * - Evitar LazyInitializationException quando a API mapeia Product -> ProductResponse fora da transação.
 * - Métodos "read" usados pela API DEVEM trazer relações inicializadas: category, subcategory, supplier.
 *
 * Convenções:
 * - "CATÁLOGO" => deleted=false AND active=true
 * - "ANY" => pode incluir deleted/inactive (admin/relatórios)
 */
@Repository
public interface TenantProductRepository extends JpaRepository<Product, UUID> {

    // =========================================================
    // READ SAFE (para controller mapear fora da transação)
    // =========================================================

    /**
     * Busca por id com relações carregadas (category/subcategory/supplier).
     */
    @EntityGraph(attributePaths = {"category", "subcategory", "supplier"})
    Optional<Product> findWithRelationsById(UUID id);

    /**
     * Lista paginada com relações carregadas.
     */
    @Override
    @EntityGraph(attributePaths = {"category", "subcategory", "supplier"})
    Page<Product> findAll(Pageable pageable);

    // =========================================================
    // CATÁLOGO: SOMENTE ATIVOS E NÃO DELETADOS (read-safe)
    // =========================================================

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.deleted = false
          AND p.active = true
          AND LOWER(p.brand) = LOWER(:brand)
        """)
    List<Product> findActiveNotDeletedByBrandIgnoreCase(@Param("brand") String brand);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.deleted = false
          AND p.active = true
          AND p.category.id = :categoryId
        """)
    List<Product> findActiveNotDeletedByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.deleted = false
          AND p.active = true
          AND p.subcategory.id = :subcategoryId
        """)
    List<Product> findActiveNotDeletedBySubcategoryId(@Param("subcategoryId") Long subcategoryId);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.deleted = false
          AND p.active = true
          AND p.supplier.id = :supplierId
        """)
    List<Product> findActiveNotDeletedBySupplierId(@Param("supplierId") UUID supplierId);

    // =========================================================
    // SEARCH (read-safe)
    // =========================================================

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:minStock IS NULL OR p.stockQuantity >= :minStock)
          AND (:maxStock IS NULL OR p.stockQuantity <= :maxStock)
        """)
    List<Product> searchProducts(@Param("name") String name,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("minStock") Integer minStock,
                                 @Param("maxStock") Integer maxStock);

    // =========================================================
    // Outros métodos "de apoio" (mantidos)
    // =========================================================

    Optional<Product> findBySku(String sku);

    List<Product> findByStockQuantityLessThan(Integer quantity);

    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= :threshold")
    Long countLowStock(@Param("threshold") Integer threshold);

    @Query("SELECT SUM(p.stockQuantity * p.price) FROM Product p")
    BigDecimal calculateTotalInventoryValue();

    @Query("SELECT p.supplier.id, COUNT(p) FROM Product p GROUP BY p.supplier.id")
    List<Object[]> countProductsBySupplier();

    // =========================================================
    // ANY (admin/relatórios) - read-safe
    // =========================================================

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE LOWER(p.brand) = LOWER(:brand)
        """)
    List<Product> findAnyByBrandIgnoreCase(@Param("brand") String brand);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.category.id = :categoryId
        """)
    List<Product> findAnyByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.subcategory.id = :subcategoryId
        """)
    List<Product> findAnyBySubcategoryId(@Param("subcategoryId") Long subcategoryId);

    // =========================================================
    // WRITE SAFETY (uniqueness + soft delete aware)
    // =========================================================

    Optional<Product> findByIdAndDeletedFalse(UUID id);

    boolean existsBySkuAndDeletedFalse(String sku);

    @Query("""
        SELECT (COUNT(p) > 0) FROM Product p
        WHERE p.deleted = false
          AND p.sku = :sku
          AND (:excludeId IS NULL OR p.id <> :excludeId)
        """)
    boolean existsSkuNotDeletedExcludingId(@Param("sku") String sku,
                                          @Param("excludeId") UUID excludeId);

    // =========================================================
    // Aliases semânticos (o service chama esses)
    // =========================================================

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     */
    default List<Product> findByCategoryId(Long categoryId) {
        return findActiveNotDeletedByCategoryId(categoryId);
    }

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     */
    default List<Product> findBySubcategoryId(Long subcategoryId) {
        return findActiveNotDeletedBySubcategoryId(subcategoryId);
    }

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     */
    default List<Product> findBySupplierId(UUID supplierId) {
        return findActiveNotDeletedBySupplierId(supplierId);
    }

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     */
    default List<Product> findByBrandIgnoreCase(String brand) {
        return findActiveNotDeletedByBrandIgnoreCase(brand);
    }

    default Long countLowStockProducts(Integer threshold) {
        return countLowStock(threshold);
    }
}