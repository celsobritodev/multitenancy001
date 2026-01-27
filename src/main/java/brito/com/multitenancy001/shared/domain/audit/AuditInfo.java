package brito.com.multitenancy001.shared.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
public class AuditInfo {

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_by_email", length = 150)
    private String createdByEmail;

    @Column(name = "updated_by_email", length = 150)
    private String updatedByEmail;

    @Column(name = "deleted_by_email", length = 150)
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
        if (email == null) return null;
        String v = email.trim().toLowerCase();
        return v.length() <= 150 ? v : v.substring(0, 150);
    }
}
