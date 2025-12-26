package brito.com.multitenancy001.repositories.product;


import brito.com.multitenancy001.entities.tenant.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {}