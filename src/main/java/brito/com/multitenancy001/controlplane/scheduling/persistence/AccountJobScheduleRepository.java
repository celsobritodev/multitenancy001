package brito.com.multitenancy001.controlplane.scheduling.persistence;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountJobScheduleRepository extends JpaRepository<AccountJobSchedule, Long> {

    Optional<AccountJobSchedule> findByAccountIdAndJobKey(Long accountId, String jobKey);

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
