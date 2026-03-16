package brito.com.multitenancy001.tenant.products.persistence;

import brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData;
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
 * <p>Objetivos:</p>
 * <ul>
 *   <li>Evitar LazyInitializationException quando a API mapeia Product -> ProductResponse fora da transação</li>
 *   <li>Métodos "read" usados pela API DEVEM trazer relações inicializadas: category, subcategory, supplier</li>
 *   <li>Dar suporte à contagem de produtos ativos/não deletados para controle de assinatura/limites</li>
 * </ul>
 *
 * <p>Convenções:</p>
 * <ul>
 *   <li>"CATÁLOGO" => deleted=false AND active=true</li>
 *   <li>"ANY" => pode incluir deleted/inactive (admin/relatórios)</li>
 * </ul>
 */
@Repository
public interface TenantProductRepository extends JpaRepository<Product, UUID> {

    // =========================================================
    // READ SAFE (para controller mapear fora da transação)
    // =========================================================

    /**
     * Busca por id com relações carregadas (category/subcategory/supplier).
     *
     * @param id id do produto
     * @return produto com relações carregadas
     */
    @EntityGraph(attributePaths = {"category", "subcategory", "supplier"})
    Optional<Product> findWithRelationsById(UUID id);

    /**
     * Lista paginada com relações carregadas.
     *
     * @param pageable paginação
     * @return página de produtos com relações carregadas
     */
    @Override
    @EntityGraph(attributePaths = {"category", "subcategory", "supplier"})
    Page<Product> findAll(Pageable pageable);

    // =========================================================
    // CATÁLOGO: SOMENTE ATIVOS E NÃO DELETADOS (read-safe)
    // =========================================================

    /**
     * Busca produtos de catálogo por brand, ignorando case.
     *
     * @param brand marca
     * @return produtos ativos e não deletados
     */
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

    /**
     * Busca produtos de catálogo por categoryId.
     *
     * @param categoryId id da categoria
     * @return produtos ativos e não deletados
     */
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

    /**
     * Busca produtos de catálogo por subcategoryId.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos ativos e não deletados
     */
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

    /**
     * Busca produtos de catálogo por supplierId.
     *
     * @param supplierId id do supplier
     * @return produtos ativos e não deletados
     */
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

    /**
     * Busca avançada de produtos com relações carregadas.
     *
     * @param name nome parcial
     * @param minPrice preço mínimo
     * @param maxPrice preço máximo
     * @param minStock estoque mínimo
     * @param maxStock estoque máximo
     * @return lista de produtos compatíveis
     */
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

    /**
     * Busca produto por SKU.
     *
     * @param sku sku
     * @return produto
     */
    Optional<Product> findBySku(String sku);

    /**
     * Busca produtos com estoque abaixo do valor informado.
     *
     * @param quantity limite de estoque
     * @return lista de produtos
     */
    List<Product> findByStockQuantityLessThan(Integer quantity);

    /**
     * Busca produtos por faixa de preço.
     *
     * @param minPrice preço mínimo
     * @param maxPrice preço máximo
     * @return lista de produtos
     */
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * Conta produtos com estoque baixo.
     *
     * @param threshold limiar de estoque
     * @return quantidade
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= :threshold")
    Long countLowStock(@Param("threshold") Integer threshold);

    /**
     * Calcula valor total do inventário.
     *
     * @return valor total
     */
    @Query("SELECT SUM(p.stockQuantity * p.price) FROM Product p")
    BigDecimal calculateTotalInventoryValue();

    /**
     * Conta produtos por supplier em formato tipado.
     *
     * @return lista supplier -> count
     */
    @Query("""
        SELECT new brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData(
            p.supplier.id,
            COUNT(p)
        )
        FROM Product p
        GROUP BY p.supplier.id
        """)
    List<SupplierProductCountData> countProductsBySupplier();

    // =========================================================
    // ANY (admin/relatórios) - read-safe
    // =========================================================

    /**
     * Busca produtos por brand sem filtrar active/deleted.
     *
     * @param brand marca
     * @return lista de produtos
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE LOWER(p.brand) = LOWER(:brand)
        """)
    List<Product> findAnyByBrandIgnoreCase(@Param("brand") String brand);

    /**
     * Busca produtos por categoryId sem filtrar active/deleted.
     *
     * @param categoryId id da categoria
     * @return lista de produtos
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.subcategory
        LEFT JOIN FETCH p.supplier
        WHERE p.category.id = :categoryId
        """)
    List<Product> findAnyByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Busca produtos por subcategoryId sem filtrar active/deleted.
     *
     * @param subcategoryId id da subcategoria
     * @return lista de produtos
     */
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

    /**
     * Busca produto por id não deletado.
     *
     * @param id id do produto
     * @return produto
     */
    Optional<Product> findByIdAndDeletedFalse(UUID id);

    /**
     * Verifica existência de SKU em produto não deletado.
     *
     * @param sku sku
     * @return true se existir
     */
    boolean existsBySkuAndDeletedFalse(String sku);

    /**
     * Verifica existência de SKU em produto não deletado,
     * excluindo um id específico.
     *
     * @param sku sku
     * @param excludeId id a excluir da checagem
     * @return true se existir outro produto com o mesmo sku
     */
    @Query("""
        SELECT (COUNT(p) > 0) FROM Product p
        WHERE p.deleted = false
          AND p.sku = :sku
          AND (:excludeId IS NULL OR p.id <> :excludeId)
        """)
    boolean existsSkuNotDeletedExcludingId(@Param("sku") String sku,
                                           @Param("excludeId") UUID excludeId);

    // =========================================================
    // SUBSCRIPTION / PLAN USAGE
    // =========================================================

    /**
     * Conta produtos não deletados no tenant atual.
     *
     * <p>Usado pela camada de assinatura para avaliar elegibilidade
     * de downgrade e exposição de limites/uso.</p>
     *
     * @return quantidade de produtos não deletados
     */
    long countByDeletedFalse();

    // =========================================================
    // Aliases semânticos (o service chama esses)
    // =========================================================

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     *
     * @param categoryId id da categoria
     * @return lista de produtos
     */
    default List<Product> findByCategoryId(Long categoryId) {
        return findActiveNotDeletedByCategoryId(categoryId);
    }

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     *
     * @param subcategoryId id da subcategoria
     * @return lista de produtos
     */
    default List<Product> findBySubcategoryId(Long subcategoryId) {
        return findActiveNotDeletedBySubcategoryId(subcategoryId);
    }

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     *
     * @param supplierId id do supplier
     * @return lista de produtos
     */
    default List<Product> findBySupplierId(UUID supplierId) {
        return findActiveNotDeletedBySupplierId(supplierId);
    }

    /**
     * Padrão: catálogo (active + notDeleted) e read-safe.
     *
     * @param brand marca
     * @return lista de produtos
     */
    default List<Product> findByBrandIgnoreCase(String brand) {
        return findActiveNotDeletedByBrandIgnoreCase(brand);
    }

    /**
     * Alias semântico para contagem de baixo estoque.
     *
     * @param threshold limiar
     * @return quantidade
     */
    default Long countLowStockProducts(Integer threshold) {
        return countLowStock(threshold);
    }
}