package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallenge;
import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallengeRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLoginChallengeService {

    private static final Duration DEFAULT_EXPIRES_IN = Duration.ofMinutes(10);

    private final TenantLoginChallengeRepository repo;
    private final AppClock appClock;

    /**
     * Cria um challenge para seleção de tenant quando:
     * - o email+senha são válidos em mais de 1 account.
     *
     * Salva no PUBLIC schema (entity é schema public).
     * Retorna apenas o UUID (mais conveniente para o fluxo).
     */
    public UUID createChallenge(String email, Set<Long> candidateAccountIds) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        if (candidateAccountIds == null || candidateAccountIds.isEmpty()) {
            throw new ApiException("INVALID_CHALLENGE", "candidateAccountIds é obrigatório", 400);
        }

        LocalDateTime now = appClock.now();
        LocalDateTime expiresAt = now.plus(DEFAULT_EXPIRES_IN);

        TenantLoginChallenge challenge = new TenantLoginChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setEmail(emailNorm);
        challenge.setCandidateAccountIds(candidateAccountIds);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(expiresAt);
        challenge.setUsedAt(null);

        repo.save(challenge);

        return challenge.getId();
    }

    /**
     * Valida:
     * - existe
     * - não usado
     * - não expirado
     */
    public TenantLoginChallenge requireValid(UUID id) {
        if (id == null) {
            throw new ApiException("INVALID_CHALLENGE_ID", "challengeId é obrigatório", 400);
        }

        TenantLoginChallenge ch = repo.findById(id).orElse(null);
        if (ch == null) {
            throw new ApiException("CHALLENGE_NOT_FOUND", "Challenge não encontrado", 404);
        }

        if (ch.isUsed()) {
            throw new ApiException("CHALLENGE_ALREADY_USED", "Challenge já foi usado", 409);
        }

        LocalDateTime now = appClock.now();
        if (ch.getExpiresAt() != null && ch.getExpiresAt().isBefore(now)) {
            throw new ApiException("CHALLENGE_EXPIRED", "Challenge expirado", 410);
        }

        return ch;
    }

    /**
     * Marca como usado. Idempotente: se já tiver usado_at, não muda.
     */
    public void markUsed(TenantLoginChallenge challenge) {
        if (challenge == null || challenge.getId() == null) return;

        if (challenge.isUsed()) return;

        challenge.setUsedAt(appClock.now());
        repo.save(challenge);
    }
}
