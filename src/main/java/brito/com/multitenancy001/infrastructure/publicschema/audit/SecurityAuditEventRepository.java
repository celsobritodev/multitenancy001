package brito.com.multitenancy001.infrastructure.publicschema.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
}
