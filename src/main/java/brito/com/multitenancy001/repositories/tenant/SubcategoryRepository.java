package brito.com.multitenancy001.repositories.tenant;

import brito.com.multitenancy001.entities.tenant.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {

    @Query("select s from Subcategory s join fetch s.category where s.id = :id")
    Optional<Subcategory> findByIdWithCategory(@Param("id") Long id);
}
