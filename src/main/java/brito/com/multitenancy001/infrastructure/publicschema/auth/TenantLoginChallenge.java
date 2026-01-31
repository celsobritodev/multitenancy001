package brito.com.multitenancy001.infrastructure.publicschema.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "tenant_login_challenges", schema = "public")
@Getter
@Setter
public class TenantLoginChallenge {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "candidate_account_ids_csv", nullable = false, columnDefinition = "text")
    private String candidateAccountIdsCsv;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

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
