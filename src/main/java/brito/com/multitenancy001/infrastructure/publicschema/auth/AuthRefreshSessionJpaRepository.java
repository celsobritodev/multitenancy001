// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/auth/AuthRefreshSessionJpaRepository.java
package brito.com.multitenancy001.infrastructure.publicschema.auth;

import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA (public schema) para sessões de refresh.
 */
public interface AuthRefreshSessionJpaRepository extends JpaRepository<AuthRefreshSessionEntity, UUID> {

    Optional<AuthRefreshSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("""
        UPDATE AuthRefreshSessionEntity s
           SET s.revokedAt = :now,
               s.revokedReasonJson = :reasonJson
         WHERE s.id = :id
           AND s.revokedAt IS NULL
    """)
    int revokeIfNotRevoked(UUID id, Instant now, String reasonJson);

    @Modifying
    @Query("""
        UPDATE AuthRefreshSessionEntity s
           SET s.revokedAt = :now,
               s.revokedReasonJson = :reasonJson
         WHERE s.sessionDomain = :domain
           AND s.accountId = :accountId
           AND s.userId = :userId
           AND s.revokedAt IS NULL
    """)
    int revokeAllForUser(AuthSessionDomain domain, Long accountId, Long userId, Instant now, String reasonJson);
}