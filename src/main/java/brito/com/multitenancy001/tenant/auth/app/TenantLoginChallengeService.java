package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantLoginChallengeStore;
import brito.com.multitenancy001.tenant.auth.domain.TenantLoginChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLoginChallengeService {

    private final TenantLoginChallengeStore store;
    private final AppClock appClock;

    private Instant now() {
        return appClock.instant();
    }

    public UUID createChallenge(String email, Set<Long> candidateAccountIds) {
        String normalizedEmail = EmailNormalizer.normalizeOrNull(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório");
        }
        if (candidateAccountIds == null || candidateAccountIds.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "candidateAccountIds é obrigatório");
        }

        return store.create(now(), normalizedEmail, candidateAccountIds);
    }

    public TenantLoginChallenge requireValid(UUID challengeId) {
        if (challengeId == null) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "challengeId é obrigatório");
        }

        return store.findValid(challengeId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CHALLENGE_NOT_FOUND, "Challenge não encontrado"));
    }

    public void markUsed(UUID challengeId) {
        if (challengeId == null) return;
        store.markUsed(challengeId, now());
    }
}
