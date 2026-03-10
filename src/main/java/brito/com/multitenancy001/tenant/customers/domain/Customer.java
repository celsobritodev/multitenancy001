// ================================================================================
// Classe: Customer
// Pacote: brito.com.multitenancy001.tenant.customers.domain
// Descrição: Aggregate Root que representa um cliente no contexto do Tenant.
//            Implementa os contratos Auditable e SoftDeletable para auditoria
//            e remoção lógica.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.domain;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Entity
@Table(name = "customers")
@EntityListeners(AuditEntityListener.class) // Delega a auditoria para o listener
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {}) // Evita loops em relacionamentos futuros
public class Customer implements Auditable, SoftDeletable {

    private static final Logger log = LoggerFactory.getLogger(Customer.class);

    // ============================================================================
    // ATRIBUTOS
    // ============================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "citext") // Case-insensitive
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String document;

    @Column(name = "document_type", length = 10)
    private String documentType;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(length = 60)
    @Builder.Default
    private String country = "Brasil";

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // ============================================================================
    // CONTRATOS (Auditable, SoftDeletable)
    // ============================================================================

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    // ============================================================================
    // MÉTODOS DE DOMÍNIO
    // ============================================================================

    /**
     * Aplica soft delete no cliente.
     * - Marca como deletado.
     * - Desativa o cliente.
     * - A data de deleção (deletedAt) e o autor (deletedBy) serão preenchidos
     *   pelo AuditEntityListener no momento da persistência.
     */
    public void softDelete() {
        if (this.deleted) {
            log.debug("Cliente ID: {} já está deletado. Ignorando softDelete.", this.id);
            return;
        }
        log.info("Aplicando soft delete no cliente ID: {} - Nome: {}", this.id, this.name);
        this.deleted = true;
        this.active = false;
        // Nota: deletedAt será setado pelo AuditEntityListener
    }

    /**
     * Restaura um cliente previamente deletado.
     * - Marca como não deletado.
     * - Reativa o cliente.
     * - Limpa os campos de auditoria de deleção (deletedAt, deletedBy, deletedByEmail).
     */
    public void restore() {
        if (!this.deleted) {
            log.debug("Cliente ID: {} não está deletado. Ignorando restore.", this.id);
            return;
        }
        log.info("Restaurando cliente ID: {} - Nome: {}", this.id, this.name);
        this.deleted = false;
        this.active = true;
        if (this.audit != null) {
            this.audit.clearDeleted(); // Limpa deletedAt, deletedBy, deletedByEmail
        }
    }

    /**
     * Atualiza o status ativo/inativo do cliente.
     */
    public void toggleActive() {
        this.active = !this.active;
        log.info("Cliente ID: {} - Status active alterado para: {}", this.id, this.active);
    }
}