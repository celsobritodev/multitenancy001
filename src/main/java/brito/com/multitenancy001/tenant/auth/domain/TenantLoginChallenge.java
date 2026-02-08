package brito.com.multitenancy001.tenant.auth.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TenantLoginChallenge(
        UUID id,
        String email,
        Set<Long> candidateAccountIds,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt
) {
    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        if (now == null) throw new IllegalArgumentException("now é obrigatório");
        if (expiresAt == null) return true;
        return now.isAfter(expiresAt);
    }
}
