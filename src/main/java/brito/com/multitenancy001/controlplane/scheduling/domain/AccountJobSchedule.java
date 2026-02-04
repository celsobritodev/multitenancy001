package brito.com.multitenancy001.controlplane.scheduling.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;

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

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
