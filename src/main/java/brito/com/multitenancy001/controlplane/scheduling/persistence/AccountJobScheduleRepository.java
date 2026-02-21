package brito.com.multitenancy001.controlplane.scheduling.persistence;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository do agendamento de jobs por account (schema public).
 */
public interface AccountJobScheduleRepository extends JpaRepository<AccountJobSchedule, Long> {

    /**
     * Retorna jobs habilitados e vencidos (due) para execução.
     */
    @Query("""
        select s
        from AccountJobSchedule s
        where s.enabled = true
          and s.nextRunAt is not null
          and s.nextRunAt <= :now
        order by s.nextRunAt asc
    """)
    List<AccountJobSchedule> findDue(@Param("now") Instant now);
}