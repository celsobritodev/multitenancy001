package brito.com.multitenancy001.infrastructure.publicschema.auth;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "tenant_login_challenges", schema = "public")
@Getter
@Setter
public class TenantLoginChallenge {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "candidate_account_ids_csv", nullable = false, columnDefinition = "text")
    private String candidateAccountIdsCsv;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    @Column(name = "used_at", columnDefinition = "timestamptz")
    private Instant usedAt;

    // =========================================================
    // FACTORY (criação explícita; sem @PrePersist)
    // =========================================================
    public static TenantLoginChallenge create(Instant now, String email, Set<Long> candidateAccountIds) {
        if (now == null) throw new IllegalArgumentException("now é obrigatório (use AppClock.instant())");

        String normalizedEmail = EmailNormalizer.normalizeOrNull(email);
        if (normalizedEmail == null) throw new IllegalArgumentException("email inválido");

        TenantLoginChallenge c = new TenantLoginChallenge();
        c.email = normalizedEmail;
        c.createdAt = now;
        c.expiresAt = now.plus(DEFAULT_TTL);
        c.setCandidateAccountIds(candidateAccountIds);
        return c;
    }

    public static TenantLoginChallenge create(Instant now, Duration ttl, String email, Set<Long> candidateAccountIds) {
        if (now == null) throw new IllegalArgumentException("now é obrigatório (use AppClock.instant())");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl inválido");

        String normalizedEmail = EmailNormalizer.normalizeOrNull(email);
        if (normalizedEmail == null) throw new IllegalArgumentException("email inválido");

        TenantLoginChallenge c = new TenantLoginChallenge();
        c.email = normalizedEmail;
        c.createdAt = now;
        c.expiresAt = now.plus(ttl);
        c.setCandidateAccountIds(candidateAccountIds);
        return c;
    }

    // =========================================================
    // DOMAIN
    // =========================================================
    public boolean isExpired(Instant now) {
        if (now == null) throw new IllegalArgumentException("now é obrigatório");
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        if (now == null) throw new IllegalArgumentException("now é obrigatório");
        if (this.usedAt != null) return;
        this.usedAt = now;
    }

    public Set<Long> candidateAccountIds() {
        if (candidateAccountIdsCsv == null || candidateAccountIdsCsv.isBlank()) return Set.of();

        return Arrays.stream(candidateAccountIdsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setCandidateAccountIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            this.candidateAccountIdsCsv = "";
            return;
        }
        this.candidateAccountIdsCsv = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
