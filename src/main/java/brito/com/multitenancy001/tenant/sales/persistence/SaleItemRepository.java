package brito.com.multitenancy001.tenant.sales.persistence;

import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {}