package brito.com.multitenancy001.tenant.suppliers.persistence;

import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA para Supplier (contexto Tenant).
 *
 * Contratos (padrão Categories/Subcategories):
 * - "NotDeleted": nunca retorna registros soft-deletados (deleted = false)
 * - "ActiveNotDeleted": retorna apenas ativos e não deletados
 * - "Any": pode incluir deleted/inactive (uso admin/diagnóstico)
 *
 * Observação:
 * - O Service deve preferir SEMPRE os métodos NotDeleted/ActiveNotDeleted.
 * - Métodos legados foram mantidos por compatibilidade, mas os recomendados são os padronizados abaixo.
 */
@Repository
public interface TenantSupplierRepository extends JpaRepository<Supplier, UUID> {

    // =========================================================
    // PADRÃO (usados pelo Service / endpoints)
    // =========================================================

    @Query("SELECT s FROM Supplier s WHERE s.deleted = false ORDER BY s.name ASC")
    List<Supplier> findAllNotDeleted();
    //  lista fornecedores não-deletados ordenados por nome.

    @Query("SELECT s FROM Supplier s WHERE s.deleted = false AND s.active = true ORDER BY s.name ASC")
    List<Supplier> findAllActiveNotDeleted();
    //  lista fornecedores ativos e não-deletados ordenados por nome.

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.deleted = false
              AND LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))
            ORDER BY s.name ASC
            """)
    List<Supplier> searchNotDeletedByName(@Param("name") String name);
    //  busca por name (contains), não-deletados.

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.deleted = false
              AND s.email = :email
            ORDER BY s.name ASC
            """)
    List<Supplier> findNotDeletedByEmail(@Param("email") String email);
    //  busca por email (exato) em não-deletados.

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.deleted = false
              AND s.document IS NOT NULL
              AND TRIM(s.document) <> ''
              AND LOWER(TRIM(s.document)) = LOWER(TRIM(:document))
            """)
    Optional<Supplier> findNotDeletedByDocumentIgnoreCase(@Param("document") String document);
    //  busca por document (trim + ignoreCase) em não-deletados.

    // =========================================================
    // ANY (admin/relatórios internos) ⚠️ pode incluir deleted/inactive
    // =========================================================

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.email = :email
            ORDER BY s.name ASC
            """)
    List<Supplier> findAnyByEmail(@Param("email") String email);
    //  busca por email incluindo deletados/inativos (uso admin/diagnóstico).

    // =========================================================
    // LEGADOS / COMPATIBILIDADE (podem ser removidos depois)
    // =========================================================

    @Query("SELECT s FROM Supplier s WHERE s.deleted = false ORDER BY s.name ASC")
    List<Supplier> findNotDeleted();
    //  legado (equivalente a findAllNotDeleted).

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.deleted = false
              AND LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))
            ORDER BY s.name ASC
            """)
    List<Supplier> findNotDeletedByNameContainingIgnoreCase(@Param("name") String name);
    //  legado (equivalente a searchNotDeletedByName).

    @Query("SELECT s FROM Supplier s WHERE s.deleted = false AND s.active = true ORDER BY s.name ASC")
    List<Supplier> findActiveNotDeleted();
    //  legado (equivalente a findAllActiveNotDeleted).

    List<Supplier> findByNameContainingIgnoreCase(String name);
    //  legado (pode incluir deletados). Evitar em regra normal.
}