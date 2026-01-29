package brito.com.multitenancy001.tenant.application.auth;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.persistence.auth.TenantLoginChallenge;
import brito.com.multitenancy001.shared.persistence.auth.TenantLoginChallengeRepository;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLoginChallengeService {

    private final TenantLoginChallengeRepository tenantLoginChallengeRepository;
    private final AppClock appClock;

    private LocalDateTime now() { return appClock.now(); }

    private LocalDateTime defaultExpiresAt() {
        return now().plusMinutes(5);
    }

    public UUID createChallenge(String email, Set<Long> candidateAccountIds) {
        if (email == null || email.isBlank()) {
            throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        }
        if (candidateAccountIds == null || candidateAccountIds.isEmpty()) {
            throw new ApiException("INVALID_STATE", "candidates não podem ser vazios", 500);
        }

        TenantLoginChallenge c = new TenantLoginChallenge();
        c.setId(UUID.randomUUID());
        c.setEmail(email.trim().toLowerCase());
        c.setCandidateAccountIds(candidateAccountIds);
        c.setCreatedAt(now());
        c.setExpiresAt(defaultExpiresAt());

        tenantLoginChallengeRepository.save(c);
        return c.getId();
    }

    public TenantLoginChallenge requireValid(UUID challengeId) {
        if (challengeId == null) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId é obrigatório", 400);
        }

        return tenantLoginChallengeRepository
                .findByIdAndExpiresAtAfterAndUsedAtIsNull(challengeId, now())
                .orElseThrow(() -> new ApiException("INVALID_CHALLENGE", "challengeId inválido ou expirado", 401));
    }

    public void markUsed(TenantLoginChallenge challenge) {
        if (challenge == null) return;
        challenge.setUsedAt(now());
        tenantLoginChallengeRepository.save(challenge);
    }
}
