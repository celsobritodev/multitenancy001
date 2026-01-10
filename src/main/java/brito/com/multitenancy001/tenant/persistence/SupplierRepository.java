package brito.com.multitenancy001.tenant.persistence;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.domain.supplier.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    
    Optional<Supplier> findByDocument(String document);
    
    List<Supplier> findByNameContainingIgnoreCase(String name);
    
    List<Supplier> findByEmail(String email);
}