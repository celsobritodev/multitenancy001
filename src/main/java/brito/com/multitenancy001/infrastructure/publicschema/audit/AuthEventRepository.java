package brito.com.multitenancy001.infrastructure.publicschema.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {
}

