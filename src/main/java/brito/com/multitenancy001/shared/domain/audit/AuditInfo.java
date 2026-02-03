package brito.com.multitenancy001.shared.domain.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.NoArgsConstructor;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;

@Getter
@Embeddable
@NoArgsConstructor
public class AuditInfo {

    private static final int AUDIT_EMAIL_MAX_LEN = 120;

    // ===== TEMPO (padronizado: Instant <-> TIMESTAMPTZ)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private Instant deletedAt;

    // ===== ATOR
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_by_email", length = AUDIT_EMAIL_MAX_LEN)
    private String createdByEmail;

    @Column(name = "updated_by_email", length = AUDIT_EMAIL_MAX_LEN)
    private String updatedByEmail;

    @Column(name = "deleted_by_email", length = AUDIT_EMAIL_MAX_LEN)
    private String deletedByEmail;

    public void onCreate(AuditActor actor, Instant now) {
        if (now == null) return;

        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;

        if (actor == null) return;

        if (this.createdBy == null) this.createdBy = actor.userId();
        if (this.createdByEmail == null) this.createdByEmail = safeEmail(actor.email());

        this.updatedBy = actor.userId();
        this.updatedByEmail = safeEmail(actor.email());
    }

    public void onUpdate(AuditActor actor, Instant now) {
        if (now == null) return;

        // nunca mexe createdAt em update
        if (this.createdAt == null) this.createdAt = now; // proteção (caso entidade legado)
        this.updatedAt = now;

        if (actor == null) return;

        this.updatedBy = actor.userId();
        this.updatedByEmail = safeEmail(actor.email());
    }

    public void onDelete(AuditActor actor, Instant now) {
        if (now == null) return;

        if (this.deletedAt == null) this.deletedAt = now;

        if (actor == null) return;

        this.deletedBy = actor.userId();
        this.deletedByEmail = safeEmail(actor.email());
    }

    private static String safeEmail(String email) {
        String v = EmailNormalizer.normalizeOrNull(email);
        if (v == null) return null;

        if (v.length() <= AUDIT_EMAIL_MAX_LEN) return v;
        return v.substring(0, AUDIT_EMAIL_MAX_LEN);
    }
}
