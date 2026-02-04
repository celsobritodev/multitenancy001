package brito.com.multitenancy001.infrastructure.publicschema.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "tenant_login_challenges", schema = "public")
@Getter
@Setter
public class TenantLoginChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    // migration: CITEXT (recomendado explicitar)
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

    @PrePersist
    private void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.candidateAccountIdsCsv == null) this.candidateAccountIdsCsv = "";
        if (this.email != null) this.email = this.email.trim();
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

    public boolean isUsed() {
        return usedAt != null;
    }
}
