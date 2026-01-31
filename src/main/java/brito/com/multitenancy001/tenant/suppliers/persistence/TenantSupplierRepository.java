package brito.com.multitenancy001.tenant.suppliers.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantSupplierRepository extends JpaRepository<Supplier, UUID> {

	// =========================
	// FINDs "crus" (podem incluir deletados) - mantenho, mas no service vamos usar
	// os "ativos/notDeleted"
	// =========================
	

	 @Query("SELECT s FROM Supplier s WHERE s.deleted = false ORDER BY s.name ASC")
	    List<Supplier> findNotDeleted();

	    @Query("SELECT s FROM Supplier s WHERE s.deleted = false AND LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY s.name ASC")
	    List<Supplier> findNotDeletedByNameContainingIgnoreCase(@Param("name") String name);

	    @Query("SELECT s FROM Supplier s WHERE s.deleted = false AND s.email = :email ORDER BY s.name ASC")
	    List<Supplier> findNotDeletedByEmail(@Param("email") String email);
	
	List<Supplier> findByNameContainingIgnoreCase(String name);

	// =========================
	// RECOMENDADO: queries usadas pelos endpoints (não-deletados)
	// =========================

	
	@Query("""
			SELECT s FROM Supplier s
			WHERE s.deleted = false
			  AND s.document IS NOT NULL
			  AND TRIM(s.document) <> ''
			  AND LOWER(TRIM(s.document)) = LOWER(TRIM(:document))
			""")
	Optional<Supplier> findNotDeletedByDocumentIgnoreCase(@Param("document") String document);

	@Query("SELECT s FROM Supplier s WHERE s.deleted = false AND s.active = true ORDER BY s.name ASC")
	List<Supplier> findActiveNotDeleted();
	
 
    
    // =========================================================
    // ANY (admin/relatórios internos) ⚠️ pode incluir deleted/inactive
    // =========================================================

    /**
     * ⚠️ Pode incluir deleted/inactive.
     * Prefira findNotDeletedByEmail / findActiveNotDeleted quando for regra normal.
     */
    List<Supplier> findAnyByEmail(String email);

   



}
