package brito.com.multitenancy001.controlplane.scheduling.domain;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidade de persistência responsável por representar a configuração de
 * agendamento de jobs internos por conta no Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Persistir a configuração recorrente de execução de um job interno.</li>
 *   <li>Armazenar o horário civil da conta ({@link LocalTime}) e a timezone
 *       IANA ({@code zoneId}) usados no cálculo do próximo run.</li>
 *   <li>Armazenar os instantes absolutos de execução e auditoria usando
 *       {@link Instant}.</li>
 * </ul>
 *
 * <p>Regras de modelagem:</p>
 * <ul>
 *   <li>{@code localTime} representa horário civil da conta.</li>
 *   <li>{@code zoneId} representa timezone IANA, por exemplo
 *       {@code America/Sao_Paulo}.</li>
 *   <li>{@code lastRunAt}, {@code nextRunAt}, {@code createdAt} e
 *       {@code updatedAt} devem ser persistidos como {@code TIMESTAMPTZ}.</li>
 *   <li>O cálculo do próximo run pertence à camada de aplicação e não à
 *       entidade.</li>
 * </ul>
 *
 * <p>Observações arquiteturais:</p>
 * <ul>
 *   <li>O vínculo com a conta é mantido por {@code accountId} escalar para
 *       evitar acoplamento desnecessário com outro agregado.</li>
 *   <li>Os timestamps devem ser preenchidos pela aplicação usando
 *       {@code AppClock} como única fonte de tempo.</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(
        name = "account_job_schedules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_account_job_schedules",
                        columnNames = {"account_id", "job_key"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_account_job_schedules_next_run",
                        columnList = "enabled, next_run_at"
                )
        }
)
public class AccountJobSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identificador da conta proprietária do agendamento.
     */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * Chave semântica do job interno.
     */
    @Column(name = "job_key", nullable = false, length = 80)
    private String jobKey;

    /**
     * Horário civil configurado para execução.
     */
    @Column(name = "local_time", nullable = false)
    private LocalTime localTime;

    /**
     * Timezone IANA do agendamento.
     */
    @Column(name = "zone_id", nullable = false, length = 60)
    private String zoneId;

    /**
     * Indica se o agendamento está habilitado.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Último instante real em que o job foi executado.
     */
    @Column(name = "last_run_at", columnDefinition = "TIMESTAMPTZ")
    private Instant lastRunAt;

    /**
     * Próximo instante real previsto para execução.
     */
    @Column(name = "next_run_at", columnDefinition = "TIMESTAMPTZ")
    private Instant nextRunAt;

    /**
     * Instante de criação do registro.
     */
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    /**
     * Instante da última atualização do registro.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;
}