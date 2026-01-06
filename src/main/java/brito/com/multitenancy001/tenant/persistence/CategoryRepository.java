package brito.com.multitenancy001.tenant.persistence;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.model.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {}