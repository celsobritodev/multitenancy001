package brito.com.multitenancy001.shared.domain.audit;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
public class AuditInfo {

    /**
     * IMPORTANTE:
     * - As colunas created_by_email / updated_by_email / deleted_by_email
     *   estão padronizadas como CITEXT (case-insensitive) no Postgres.
     * - Mesmo assim, mantemos limite lógico de 120 chars para proteger persistência
     *   e padronizar payloads.
     */
    private static final int AUDIT_EMAIL_MAX_LEN = 120;

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

    public void onCreate(AuditActor actor) {
        if (actor == null) return;

        if (this.createdBy == null) this.createdBy = actor.userId();
        if (this.createdByEmail == null) this.createdByEmail = safeEmail(actor.email());

        this.updatedBy = actor.userId();
        this.updatedByEmail = safeEmail(actor.email());
    }

    public void onUpdate(AuditActor actor) {
        if (actor == null) return;

        this.updatedBy = actor.userId();
        this.updatedByEmail = safeEmail(actor.email());
    }

    public void onDelete(AuditActor actor) {
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
