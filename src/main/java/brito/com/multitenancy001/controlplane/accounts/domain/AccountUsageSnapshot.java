package brito.com.multitenancy001.controlplane.accounts.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Snapshot materializado do uso atual da conta no schema public.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Desacoplar o núcleo de subscription do acesso síncrono ao tenant.</li>
 *   <li>Materializar a visão pública de uso por conta.</li>
 *   <li>Servir como fonte de leitura para preview, elegibilidade e consulta administrativa.</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Há no máximo um snapshot por conta.</li>
 *   <li>Os contadores sempre são não negativos.</li>
 *   <li>{@code measuredAt} representa o instante de medição do uso materializado.</li>
 * </ul>
 */
@Entity
@Table(name = "account_usage_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class AccountUsageSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id da conta dona do snapshot.
     */
    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    /**
     * Quantidade atual de usuários computada para a conta.
     */
    @Column(name = "current_users", nullable = false)
    private long currentUsers;

    /**
     * Quantidade atual de produtos computada para a conta.
     */
    @Column(name = "current_products", nullable = false)
    private long currentProducts;

    /**
     * Storage atual computado para a conta, em MB.
     */
    @Column(name = "current_storage_mb", nullable = false)
    private long currentStorageMb;

    /**
     * Instante em que os valores de uso foram medidos.
     */
    @Column(name = "measured_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant measuredAt;

    /**
     * Instante de criação do registro.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    /**
     * Instante da última atualização do registro.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;
}