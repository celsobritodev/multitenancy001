// ================================================================================
// Interface: TenantCustomerRepository
// Pacote: brito.com.multitenancy001.tenant.customers.persistence
// Descrição: Repositório JPA para a entidade Customer no contexto do Tenant.
//            Convenções:
//            - NotDeleted: exclui registros com soft delete.
//            - ActiveNotDeleted: apenas ativos e não deletados.
//            - Deleted: apenas registros deletados.
//            - Any: inclui deletados.
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

    /**
     * Lista todos os clientes não deletados, ordenados por nome.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.deleted = false
            ORDER BY c.name ASC
            """)
    List<Customer> findAllNotDeleted();

    /**
     * Lista todos os clientes ativos e não deletados, ordenados por nome.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.deleted = false
              AND c.active = true
            ORDER BY c.name ASC
            """)
    List<Customer> findAllActiveNotDeleted();

    /**
     * Busca um cliente não deletado por ID.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.id = :id
              AND c.deleted = false
            """)
    Optional<Customer> findByIdNotDeleted(@Param("id") UUID id);

    /**
     * Busca um cliente deletado por ID.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.id = :id
              AND c.deleted = true
            """)
    Optional<Customer> findDeletedById(@Param("id") UUID id);

    /**
     * Busca um cliente por ID, incluindo deletados.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.id = :id
            """)
    Optional<Customer> findAnyById(@Param("id") UUID id);

    /**
     * Busca clientes não deletados cujo nome contenha o termo.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.deleted = false
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))
            ORDER BY c.name ASC
            """)
    List<Customer> searchNotDeletedByName(@Param("name") String name);

    /**
     * Busca um cliente não deletado por documento.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.deleted = false
              AND c.document IS NOT NULL
              AND LOWER(c.document) = LOWER(:document)
            """)
    Optional<Customer> findNotDeletedByDocument(@Param("document") String document);

    /**
     * Busca clientes não deletados por email.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.deleted = false
              AND c.email = :email
            """)
    List<Customer> findNotDeletedByEmail(@Param("email") String email);

    /**
     * Busca clientes por email, incluindo deletados.
     */
    @Query("""
            SELECT c
            FROM Customer c
            WHERE c.email = :email
            """)
    List<Customer> findAnyByEmail(@Param("email") String email);
}