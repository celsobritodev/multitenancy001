package brito.com.multitenancy001.tenant.auth.app.boundary;

import brito.com.multitenancy001.tenant.auth.domain.TenantLoginChallenge;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TenantLoginChallengeStore {

    UUID create(Instant now, String normalizedEmail, Set<Long> candidateAccountIds);

    Optional<TenantLoginChallenge> findValid(UUID id, Instant now);

    void markUsed(UUID id, Instant now);
}
