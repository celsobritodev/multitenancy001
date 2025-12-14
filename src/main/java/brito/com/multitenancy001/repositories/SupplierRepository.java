package brito.com.multitenancy001.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.entities.tenant.Supplier;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, String> {
    
    Optional<Supplier> findByDocument(String document);
    
    List<Supplier> findByNameContainingIgnoreCase(String name);
    
    List<Supplier> findByEmail(String email);
}