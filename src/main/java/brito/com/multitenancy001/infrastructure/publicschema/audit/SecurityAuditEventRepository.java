package brito.com.multitenancy001.infrastructure.publicschema.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {



// Adicionar métodos de consulta:

    Page<SecurityAuditEvent> findByActionType(SecurityAuditActionType actionType, Pageable pageable);

    Page<SecurityAuditEvent> findByActorUserId(Long actorUserId, Pageable pageable);

    Page<SecurityAuditEvent> findByTargetUserId(Long targetUserId, Pageable pageable);

    Page<SecurityAuditEvent> findByAccountId(Long accountId, Pageable pageable);

    Page<SecurityAuditEvent> findByOccurredAtBetween(Instant start, Instant end, Pageable pageable);

    @Query("""
        SELECT e FROM SecurityAuditEvent e
        WHERE (:actionType IS NULL OR e.actionType = :actionType)
          AND (:actorUserId IS NULL OR e.actorUserId = :actorUserId)
          AND (:targetUserId IS NULL OR e.targetUserId = :targetUserId)
          AND (:accountId IS NULL OR e.accountId = :accountId)
          AND (:outcome IS NULL OR e.outcome = :outcome)
          AND (:start IS NULL OR e.occurredAt >= :start)
          AND (:end IS NULL OR e.occurredAt <= :end)
        ORDER BY e.occurredAt DESC
        """)
    Page<SecurityAuditEvent> search(
            @Param("actionType") SecurityAuditActionType actionType,
            @Param("actorUserId") Long actorUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("accountId") Long accountId,
            @Param("outcome") AuditOutcome outcome,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable
    );
    
}
