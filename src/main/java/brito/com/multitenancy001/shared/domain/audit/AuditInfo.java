package brito.com.multitenancy001.shared.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * AuditInfo é a fonte única de auditoria do projeto.
 *
 * Regras:
 * - Instantes reais => Instant (TIMESTAMPTZ).
 * - Ator vem de AuditActorProviders (via listener).
 * - AppClock deve ser a única fonte de tempo (now nunca pode ser null).
 */
@Embeddable
@Getter
@Setter
public class AuditInfo {

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private Instant deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "updated_by_email")
    private String updatedByEmail;

    @Column(name = "deleted_by_email")
    private String deletedByEmail;

    // =========================
    // API SEMÂNTICA (usada pelo AuditEntityListener)
    // =========================

    public void onCreate(AuditActor actor, Instant now) {
        Objects.requireNonNull(now, "AuditInfo.onCreate: now não pode ser null (AppClock é obrigatório)");

        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;

        if (actor != null) {
            if (this.createdBy == null) {
                this.createdBy = actor.userId();
                this.createdByEmail = actor.email();
            }
            this.updatedBy = actor.userId();
            this.updatedByEmail = actor.email();
        }
    }

    public void onUpdate(AuditActor actor, Instant now) {
        Objects.requireNonNull(now, "AuditInfo.onUpdate: now não pode ser null (AppClock é obrigatório)");

        this.updatedAt = now;

        if (actor != null) {
            this.updatedBy = actor.userId();
            this.updatedByEmail = actor.email();
        }
    }

    public void onDelete(AuditActor actor, Instant now) {
        Objects.requireNonNull(now, "AuditInfo.onDelete: now não pode ser null (AppClock é obrigatório)");

        markDeleted(now);

        if (actor != null) {
            this.deletedBy = actor.userId();
            this.deletedByEmail = actor.email();
        }
    }

    // =========================
    // API SEMÂNTICA (compat/domínio)
    // =========================

    /** Marca a entidade como deletada no instante informado (sem mexer em deletedBy). */
    public void markDeleted(Instant now) {
        Objects.requireNonNull(now, "AuditInfo.markDeleted: now não pode ser null");
        this.deletedAt = now;
    }

    /** Limpa estado de deleção (restore). */
    public void clearDeleted() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletedByEmail = null;
    }
}
