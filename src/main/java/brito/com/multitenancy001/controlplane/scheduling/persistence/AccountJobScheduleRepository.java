package brito.com.multitenancy001.controlplane.scheduling.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;

public interface AccountJobScheduleRepository extends JpaRepository<AccountJobSchedule, Long> {

    @Query("""
        select s
        from AccountJobSchedule s
        where s.enabled = true
          and s.nextRunAt is not null
          and s.nextRunAt <= :now
        order by s.nextRunAt asc
    """)
    List<AccountJobSchedule> findDue(Instant now);
}
