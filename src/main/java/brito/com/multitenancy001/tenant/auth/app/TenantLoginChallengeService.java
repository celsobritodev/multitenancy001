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

/**
 * Application Service responsável por criar e validar challenges de seleção de tenant no login.
 *
 * <p>Responsabilidade:</p>
 * <ul>
 *   <li>Criar um challenge temporário para {@code email} com um conjunto de {@code accountIds} candidatos.</li>
 *   <li>Validar challenge por id e verificar expiração/uso único.</li>
 *   <li>Marcar challenge como usado após confirmação bem-sucedida.</li>
 * </ul>
 *
 * <p>Regras de tempo:</p>
 * <ul>
 *   <li>{@code AppClock} deve ser a única fonte de tempo; esta classe não deve usar {@code Instant.now()}.</li>
 * </ul>
 *
 * <p>Persistência:</p>
 * <ul>
 *   <li>Não define tecnologia; persiste via {@code TenantLoginChallengeStore} (boundary).</li>
 * </ul>
 */
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
            throw new ApiException(ApiErrorCode.INVALID_EMAIL);
        }

        if (candidateAccountIds == null || candidateAccountIds.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE);
        }

        return store.create(now(), normalizedEmail, candidateAccountIds);
    }

    public TenantLoginChallenge requireValid(UUID challengeId) {
        if (challengeId == null) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE);
        }

        return store.findValid(challengeId, now())
                .orElseThrow(() ->
                        new ApiException(ApiErrorCode.CHALLENGE_NOT_FOUND)
                );
    }

    public void markUsed(UUID challengeId) {
        if (challengeId == null) {
            return;
        }
        store.markUsed(challengeId, now());
    }
}
