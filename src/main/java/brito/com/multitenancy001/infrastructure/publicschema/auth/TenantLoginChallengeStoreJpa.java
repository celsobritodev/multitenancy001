package brito.com.multitenancy001.infrastructure.publicschema.auth;

import brito.com.multitenancy001.tenant.auth.app.boundary.TenantLoginChallengeStore;
import brito.com.multitenancy001.tenant.auth.domain.TenantLoginChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantLoginChallengeStoreJpa implements TenantLoginChallengeStore {

    private final TenantLoginChallengeJpaRepository repo;

    @Override
    public UUID create(Instant now, String normalizedEmail, Set<Long> candidateAccountIds) {
        TenantLoginChallengeEntity e = TenantLoginChallengeEntity.create(now, normalizedEmail, candidateAccountIds);
        e = repo.save(e);
        return e.getId();
    }

    @Override
    public Optional<TenantLoginChallenge> findValid(UUID id, Instant now) {
        return repo.findByIdAndExpiresAtAfterAndUsedAtIsNull(id, now)
                .map(this::toDomain);
    }

    @Override
    public void markUsed(UUID id, Instant now) {
        repo.findById(id).ifPresent(e -> {
            if (e.isUsed()) return;
            e.markUsed(now);
            repo.save(e);
        });
    }

    private TenantLoginChallenge toDomain(TenantLoginChallengeEntity e) {
        Set<Long> ids = e.candidateAccountIds();
        if (ids == null) ids = new LinkedHashSet<>();
        return new TenantLoginChallenge(
                e.getId(),
                e.getEmail(),
                ids,
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getUsedAt()
        );
    }
}
