package brito.com.multitenancy001.shared.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * AuditInfo é a fonte única de auditoria do projeto.
 *
 * Regras:
 * - Instantes reais => Instant (TIMESTAMPTZ).
 * - Ator vem de AuditActorProviders (via listener).
 * - Deve suportar "create/update/delete" como operações semânticas
 *   para manter rastreabilidade e consistência.
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
        if (now == null) now = Instant.now();

        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;

        if (actor != null) {
            if (this.createdBy == null) this.createdBy = actor.id();
            if (this.createdByEmail == null) this.createdByEmail = actor.email();
            this.updatedBy = actor.id();
            this.updatedByEmail = actor.email();
        }
    }

    public void onUpdate(AuditActor actor, Instant now) {
        if (now == null) now = Instant.now();

        // garantia: se por algum motivo não tiver created, cria também
        if (this.createdAt == null) {
            onCreate(actor, now);
            return;
        }

        this.updatedAt = now;
        if (actor != null) {
            this.updatedBy = actor.id();
            this.updatedByEmail = actor.email();
        }
    }

    public void onDelete(AuditActor actor, Instant now) {
        if (now == null) now = Instant.now();

        // deleted é idempotente
        if (this.deletedAt == null) {
            this.deletedAt = now;
        }

        if (actor != null) {
            this.deletedBy = actor.id();
            this.deletedByEmail = actor.email();
        }

        // também atualiza updated para manter consistência de timeline
        this.updatedAt = now;
        if (actor != null) {
            this.updatedBy = actor.id();
            this.updatedByEmail = actor.email();
        }

        // garantia: se não tinha createdAt, seta
        if (this.createdAt == null) {
            this.createdAt = now;
            if (actor != null) {
                this.createdBy = actor.id();
                this.createdByEmail = actor.email();
            }
        }
    }

    // =========================
    // Compat com código antigo
    // =========================

    /** Compat antigo: deletar só setando deletedAt */
    public void markDeleted(Instant now) {
        onDelete(null, now);
    }

    public void clearDeleted() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletedByEmail = null;
    }
}
