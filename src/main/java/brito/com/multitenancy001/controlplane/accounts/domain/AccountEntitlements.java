package brito.com.multitenancy001.controlplane.accounts.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Snapshot materializado de entitlements da conta.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Persistir os limites efetivos da conta no public schema</li>
 *   <li>Representar a capacidade operacional vigente do tenant</li>
 *   <li>Servir de base para guards de quota e exposição de limites</li>
 * </ul>
 *
 * <p>Observações:</p>
 * <ul>
 *   <li>O id é compartilhado com a própria Account ({@code account_id})</li>
 *   <li>Os timestamps são explícitos para rastreabilidade operacional</li>
 *   <li>Os timestamps são preenchidos pela camada de aplicação, usando AppClock</li>
 * </ul>
 */
@Entity
@Table(name = "account_entitlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntitlements {

    /**
     * Chave primária compartilhada com a conta.
     */
    @Id
    @Column(name = "account_id")
    private Long accountId;

    /**
     * Conta dona deste snapshot de entitlement.
     */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    /**
     * Limite máximo de usuários permitido para a conta.
     */
    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    /**
     * Limite máximo de produtos permitido para a conta.
     */
    @Column(name = "max_products", nullable = false)
    private Integer maxProducts;

    /**
     * Limite máximo de armazenamento em MB permitido para a conta.
     */
    @Column(name = "max_storage_mb", nullable = false)
    private Integer maxStorageMb;

    /**
     * Timestamp de criação do snapshot materializado.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Timestamp da última atualização do snapshot materializado.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}