package brito.com.multitenancy001.shared.persistence.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TenantLoginChallengeRepository extends JpaRepository<TenantLoginChallenge, UUID> {

    Optional<TenantLoginChallenge> findByIdAndExpiresAtAfterAndUsedAtIsNull(UUID id, LocalDateTime now);
}
