// ================================================================================
// Interface: TenantCustomerRepository
// Pacote: brito.com.multitenancy001.tenant.customers.persistence
// Descrição: Repositório JPA para a entidade Customer no contexto do Tenant.
//            Define contratos de consulta seguindo as convenções do projeto:
//            - "NotDeleted": exclui registros com soft delete.
//            - "ActiveNotDeleted": exclui deletados e considera apenas ativos.
//            - "Any": pode incluir deletados (para usos administrativos).
// ================================================================================

package brito.com.multitenancy001.tenant.customers.persistence;

import brito.com.multitenancy001.tenant.customers.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantCustomerRepository extends JpaRepository<Customer, UUID> {

    // ============================================================================
    // CONSULTAS PADRÃO (NOT DELETED)
    // ============================================================================

    /**
     * Lista todos os clientes NÃO DELETADOS, ordenados por nome.
     */
    @Query("SELECT c FROM Customer c WHERE c.deleted = false ORDER BY c.name ASC")
    List<Customer> findAllNotDeleted();

    /**
     * Lista todos os clientes ATIVOS e NÃO DELETADOS, ordenados por nome.
     */
    @Query("SELECT c FROM Customer c WHERE c.deleted = false AND c.active = true ORDER BY c.name ASC")
    List<Customer> findAllActiveNotDeleted();

    /**
     * Busca um cliente por ID, garantindo que não esteja deletado.
     */
    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.deleted = false")
    Optional<Customer> findByIdNotDeleted(@Param("id") UUID id);

    /**
     * Busca clientes NÃO DELETADOS cujo nome contenha o termo (case-insensitive).
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.deleted = false
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))
            ORDER BY c.name ASC
            """)
    List<Customer> searchNotDeletedByName(@Param("name") String name);

    /**
     * Busca um cliente NÃO DELETADO por documento (exato, case-insensitive).
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.deleted = false
              AND c.document IS NOT NULL
              AND LOWER(c.document) = LOWER(:document)
            """)
    Optional<Customer> findNotDeletedByDocument(@Param("document") String document);

    /**
     * Busca clientes NÃO DELETADOS por email (exato).
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.deleted = false
              AND c.email = :email
            """)
    List<Customer> findNotDeletedByEmail(@Param("email") String email);

    // ============================================================================
    // CONSULTAS "ANY" (PODEM INCLUIR DELETADOS)
    // ============================================================================

    /**
     * Busca clientes por email, podendo incluir deletados (uso administrativo).
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.email = :email
            """)
    List<Customer> findAnyByEmail(@Param("email") String email);
}