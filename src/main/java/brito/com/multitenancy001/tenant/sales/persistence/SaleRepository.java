package brito.com.multitenancy001.tenant.sales.persistence;

import brito.com.multitenancy001.tenant.sales.domain.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdAndDeletedFalse(UUID id);

    List<Sale> findByDeletedFalseOrderBySaleDateDesc();

    List<Sale> findByDeletedFalseAndSaleDateBetweenOrderBySaleDateDesc(Instant from, Instant to);
}