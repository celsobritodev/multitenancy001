package brito.com.multitenancy001.controlplane.scheduling.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Entidade de agendamento de jobs por Account (Control Plane / schema public).
 *
 * Responsabilidade:
 * - Persistir a configuração de execução recorrente de um job por account.
 *
 * Regras:
 * - Horário civil do tenant é representado por (localTime + zoneId).
 * - Os instantes calculados para execução (lastRunAt/nextRunAt) são armazenados como Instant.
 * - Persistência deve usar timestamptz para todos os Instant.
 *
 * Observações:
 * - A entidade não executa jobs; apenas descreve quando um job deve rodar.
 * - Cálculo de próximo run pertence ao Application Service (ex.: AccountJobScheduleService).
 */



@Getter
@Setter
@Entity
@Table(name = "account_job_schedules")
public class AccountJobSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "job_key", nullable = false, length = 80)
    private String jobKey;

    @Column(name = "local_time", nullable = false)
    private LocalTime localTime;

    @Column(name = "zone_id", nullable = false, length = 60)
    private String zoneId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // ✅ Padronizado: Instant -> timestamptz
    @Column(name = "last_run_at", columnDefinition = "timestamptz")
    private Instant lastRunAt;

    // ✅ Padronizado: Instant -> timestamptz
    @Column(name = "next_run_at", columnDefinition = "timestamptz")
    private Instant nextRunAt;

    // ✅ Padronizado: Instant -> timestamptz
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    // ✅ Padronizado: Instant -> timestamptz
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;
}
