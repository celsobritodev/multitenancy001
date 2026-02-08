package brito.com.multitenancy001.infrastructure.publicschema.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TenantLoginChallengeJpaRepository extends JpaRepository<TenantLoginChallengeEntity, UUID> {

    Optional<TenantLoginChallengeEntity> findByIdAndExpiresAtAfterAndUsedAtIsNull(UUID id, Instant now);
}

