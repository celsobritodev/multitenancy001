package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallenge;
import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallengeRepository;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLoginChallengeService {

    private static final Duration DEFAULT_EXPIRES_IN = Duration.ofMinutes(10);

    private final TenantLoginChallengeRepository tenantLoginChallengeRepository;
    private final AppClock appClock;

    private Instant now() {
        return appClock.instant();
    }

    public UUID createChallenge(String email, Set<Long> candidateAccountIds) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        if (candidateAccountIds == null || candidateAccountIds.isEmpty()) {
            throw new ApiException("INVALID_CHALLENGE", "candidateAccountIds é obrigatório", 400);
        }

        Instant createdAt = now();
        Instant expiresAt = createdAt.plus(DEFAULT_EXPIRES_IN);

        TenantLoginChallenge challenge = new TenantLoginChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setEmail(emailNorm);
        challenge.setCandidateAccountIds(candidateAccountIds);
        challenge.setCreatedAt(createdAt);
        challenge.setExpiresAt(expiresAt);
        challenge.setUsedAt(null);

        tenantLoginChallengeRepository.save(challenge);

        return challenge.getId();
    }

    /**
     * ✅ Agora usa o método do repository:
     * findByIdAndExpiresAtAfterAndUsedAtIsNull(id, now)
     */
    public TenantLoginChallenge requireValid(UUID challengeId) {
        if (challengeId == null) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId é obrigatório", 400);
        }

        TenantLoginChallenge challenge = tenantLoginChallengeRepository
                .findByIdAndExpiresAtAfterAndUsedAtIsNull(challengeId, now())
                .orElseThrow(() -> new ApiException(
                        "CHALLENGE_NOT_FOUND",
                        "Challenge não encontrado, expirado ou já usado",
                        404
                ));

        return challenge;
    }

    public void markUsed(TenantLoginChallenge challenge) {
        if (challenge == null || challenge.getId() == null) return;
        if (challenge.isUsed()) return;

        challenge.setUsedAt(now());
        tenantLoginChallengeRepository.save(challenge);
    }
}

