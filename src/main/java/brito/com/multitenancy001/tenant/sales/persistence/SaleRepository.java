package brito.com.multitenancy001.tenant.sales.persistence;

import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de vendas.
 */
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    /**
     * Busca venda não deletada por ID já carregando itens.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Sale> findByIdAndDeletedFalse(UUID id);

    /**
     * Lista todas as vendas não deletadas por data desc.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseOrderBySaleDateDesc(Pageable pageable);

    /**
     * Lista vendas não deletadas por período.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndSaleDateBetweenOrderBySaleDateDesc(Instant from, Instant to, Pageable pageable);

    /**
     * Lista vendas não deletadas por status.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndStatusOrderBySaleDateDesc(SaleStatus status, Pageable pageable);

    /**
     * Lista vendas não deletadas por customer.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndCustomerIdOrderBySaleDateDesc(UUID customerId, Pageable pageable);

    /**
     * Lista vendas não deletadas por customer + status.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndCustomerIdAndStatusOrderBySaleDateDesc(UUID customerId,
                                                                           SaleStatus status,
                                                                           Pageable pageable);

    /**
     * Lista vendas não deletadas por customer + período.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndCustomerIdAndSaleDateBetweenOrderBySaleDateDesc(UUID customerId,
                                                                                    Instant from,
                                                                                    Instant to,
                                                                                    Pageable pageable);

    /**
     * Lista vendas não deletadas por status + período.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndStatusAndSaleDateBetweenOrderBySaleDateDesc(SaleStatus status,
                                                                                Instant from,
                                                                                Instant to,
                                                                                Pageable pageable);

    /**
     * Lista vendas não deletadas por customer + status + período.
     */
    @EntityGraph(attributePaths = "items")
    Page<Sale> findByDeletedFalseAndCustomerIdAndStatusAndSaleDateBetweenOrderBySaleDateDesc(UUID customerId,
                                                                                              SaleStatus status,
                                                                                              Instant from,
                                                                                              Instant to,
                                                                                              Pageable pageable);
}