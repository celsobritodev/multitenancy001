package brito.com.multitenancy001.infrastructure.publicschema.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TenantLoginChallengeRepository extends JpaRepository<TenantLoginChallenge, UUID> {

    Optional<TenantLoginChallenge> findByIdAndExpiresAtAfterAndUsedAtIsNull(UUID id, Instant now);
}

