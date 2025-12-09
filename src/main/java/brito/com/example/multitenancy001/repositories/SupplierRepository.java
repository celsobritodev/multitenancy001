package brito.com.example.multitenancy001.repositories;


import brito.com.example.multitenancy001.entities.tenant.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, String> {
    
    Optional<Supplier> findByDocument(String document);
    
    List<Supplier> findByNameContainingIgnoreCase(String name);
    
    List<Supplier> findByEmail(String email);
}