package brito.com.multitenancy001.tenant.categories.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.categories.domain.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantCategoryRepository extends JpaRepository<Category, Long> {

    // =========
    // Normal (default): NÃO retorna deletados
    // =========

    @Query("select c from Category c where c.deleted = false order by c.name asc")
    List<Category> findNotDeleted();

    @Query("select c from Category c where c.deleted = false and c.active = true order by c.name asc")
    List<Category> findNotDeletedActive();

    @Query("select c from Category c where c.deleted = false and lower(c.name) = lower(:name)")
    Optional<Category> findNotDeletedByNameIgnoreCase(@Param("name") String name);

    @Query("select c from Category c where c.deleted = false and lower(c.name) like lower(concat('%', :name, '%')) order by c.name asc")
    List<Category> findNotDeletedByNameContainingIgnoreCase(@Param("name") String name);

    // =========
    // Admin: flags (includeDeleted / includeInactive)
    // =========

    @Query("""
           select c from Category c
           where (:includeDeleted = true or c.deleted = false)
             and (:includeInactive = true or c.active = true)
           order by c.name asc
           """)
    List<Category> findWithFlags(@Param("includeDeleted") boolean includeDeleted,
                                 @Param("includeInactive") boolean includeInactive);
}
