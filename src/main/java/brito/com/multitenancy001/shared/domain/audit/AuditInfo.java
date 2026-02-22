package brito.com.multitenancy001.shared.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * AuditInfo (fonte única) — compatível com o DDL:
 * created_at/created_by/created_by_email
 * updated_at/updated_by/updated_by_email
 * deleted_at/deleted_by/deleted_by_email
 *
 * Semântica:
 * - createdAt/updatedAt/deletedAt: instantes reais (TIMESTAMPTZ)
 * - createdBy/updatedBy/deletedBy: userId (quando aplicável)
 * - createdByEmail/updatedByEmail/deletedByEmail: CITEXT
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class AuditInfo {

    @Column(name = "created_at", columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "updated_at", columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_by_email")
    private String updatedByEmail;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private Instant deletedAt;

    // ✅ NOVOS CAMPOS: Adicionados para o AuditEntityListener poder preencher
    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_by_email")
    private String deletedByEmail;

    // ---------------------------------------------------------------------
    // Lifecycle helpers (usados pelo AuditEntityListener)
    // ---------------------------------------------------------------------

    public void onCreate(AuditActor actor, Instant now) {
        if (now == null) return;

        if (this.createdAt == null) this.createdAt = now;
        if (actor != null) {
            if (this.createdBy == null) this.createdBy = actor.userId();
            if (this.createdByEmail == null) this.createdByEmail = actor.email();
        }

        // created também é update inicial
        this.updatedAt = now;
        if (actor != null) {
            this.updatedBy = actor.userId();
            this.updatedByEmail = actor.email();
        }
    }

    public void onUpdate(AuditActor actor, Instant now) {
        if (now == null) return;

        if (this.createdAt == null) {
            // fallback defensivo (não deveria acontecer em entidades normais)
            this.createdAt = now;
            if (actor != null) {
                this.createdBy = actor.userId();
                this.createdByEmail = actor.email();
            }
        }

        this.updatedAt = now;
        if (actor != null) {
            this.updatedBy = actor.userId();
            this.updatedByEmail = actor.email();
        }
    }

    public void onDelete(AuditActor actor, Instant now) {
        if (now == null) return;

        this.deletedAt = now;
        if (actor != null) {
            this.deletedBy = actor.userId();
            this.deletedByEmail = actor.email();
        }

        // deletar também conta como update
        if (this.createdAt == null) {
            this.createdAt = now;
            if (actor != null) {
                this.createdBy = actor.userId();
                this.createdByEmail = actor.email();
            }
        }

        this.updatedAt = now;
        if (actor != null) {
            this.updatedBy = actor.userId();
            this.updatedByEmail = actor.email();
        }
    }

    public void clearDeleted() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletedByEmail = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // ---------------------------------------------------------------------
    // Aliases de compatibilidade (código legado chama mark*)
    // ---------------------------------------------------------------------

    /**
     * Legado: entidades antigas chamam audit.markDeleted(now).
     * Mantemos para evitar quebrar domínio.
     */
    public void markDeleted(Instant now) {
        onDelete(null, now);
    }

    /**
     * Legado (opcional): se houver chamadas antigas.
     */
    public void markCreated(Instant now) {
        onCreate(null, now);
    }

    /**
     * Legado (opcional): se houver chamadas antigas.
     */
    public void markUpdated(Instant now) {
        onUpdate(null, now);
    }
}