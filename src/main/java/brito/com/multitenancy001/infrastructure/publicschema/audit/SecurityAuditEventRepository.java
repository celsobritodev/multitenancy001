package brito.com.multitenancy001.infrastructure.publicschema.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import brito.com.multitenancy001.infrastructure.publicschema.audit.entity.PublicSecurityAuditEvent;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;

public interface SecurityAuditEventRepository extends JpaRepository<PublicSecurityAuditEvent, Long> {



// Adicionar métodos de consulta:

    Page<PublicSecurityAuditEvent> findByActionType(SecurityAuditActionType actionType, Pageable pageable);

    Page<PublicSecurityAuditEvent> findByActorUserId(Long actorUserId, Pageable pageable);

    Page<PublicSecurityAuditEvent> findByTargetUserId(Long targetUserId, Pageable pageable);

    Page<PublicSecurityAuditEvent> findByAccountId(Long accountId, Pageable pageable);

    Page<PublicSecurityAuditEvent> findByOccurredAtBetween(Instant start, Instant end, Pageable pageable);

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
    Page<PublicSecurityAuditEvent> search(
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
