package brito.com.multitenancy001.tenant.categories.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.categories.domain.Subcategory;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSubcategoryRepository extends JpaRepository<Subcategory, Long> {

    @Query("select s from Subcategory s join fetch s.category where s.id = :id")
    Optional<Subcategory> findByIdWithCategory(@Param("id") Long id);

    // =========
    // Default: NÃO retorna deletados
    // =========

    @Query("""
           select s from Subcategory s
           where s.deleted = false
           order by s.name asc
           """)
    List<Subcategory> findNotDeleted();

    @Query("""
           select s from Subcategory s
           where s.deleted = false and s.active = true
           order by s.name asc
           """)
    List<Subcategory> findActiveNotDeleted();

    @Query("""
           select s from Subcategory s
           where s.deleted = false and s.category.id = :categoryId
           order by s.name asc
           """)
    List<Subcategory> findNotDeletedByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
           select s from Subcategory s
           where s.deleted = false and s.active = true and s.category.id = :categoryId
           order by s.name asc
           """)
    List<Subcategory> findActiveNotDeletedByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
           select s from Subcategory s
           where s.deleted = false
             and s.category.id = :categoryId
             and lower(s.name) = lower(:name)
           """)
    Optional<Subcategory> findNotDeletedByCategoryIdAndNameIgnoreCase(@Param("categoryId") Long categoryId,
                                                                      @Param("name") String name);

    // =========
    // Admin: flags (includeDeleted / includeInactive)
    // =========

    @Query("""
           select s from Subcategory s
           where s.category.id = :categoryId
             and (:includeDeleted = true or s.deleted = false)
             and (:includeInactive = true or s.active = true)
           order by s.name asc
           """)
    List<Subcategory> findByCategoryWithFlags(@Param("categoryId") Long categoryId,
                                              @Param("includeDeleted") boolean includeDeleted,
                                              @Param("includeInactive") boolean includeInactive);
}

