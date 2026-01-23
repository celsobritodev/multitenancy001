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

    @Column(name = "created_by_username", length = 120)
    private String createdByUsername;

    @Column(name = "updated_by_username", length = 120)
    private String updatedByUsername;

    @Column(name = "deleted_by_username", length = 120)
    private String deletedByUsername;

    public void onCreate(AuditActor actor) {
        if (this.createdBy == null) this.createdBy = actor.userId();
        if (this.createdByUsername == null) this.createdByUsername = actor.username();

        this.updatedBy = actor.userId();
        this.updatedByUsername = actor.username();
    }

    public void onUpdate(AuditActor actor) {
        this.updatedBy = actor.userId();
        this.updatedByUsername = actor.username();
    }

    public void onDelete(AuditActor actor) {
        this.deletedBy = actor.userId();
        this.deletedByUsername = actor.username();
    }
}
