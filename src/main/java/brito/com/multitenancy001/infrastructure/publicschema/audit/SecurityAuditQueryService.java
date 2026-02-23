// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/SecurityAuditQueryService.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Serviço de consulta para eventos de auditoria.
 * 
 * <p>Permite buscar e filtrar eventos de auditoria para
 * investigação e compliance.</p>
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final SecurityAuditEventRepository securityAuditEventRepository;

    /**
     * Busca eventos por tipo de ação.
     */
    public Page<SecurityAuditEvent> findByActionType(SecurityAuditActionType actionType, Pageable pageable) {
        return publicSchemaUnitOfWork.readOnly(() -> 
                securityAuditEventRepository.findByActionType(actionType, pageable));
    }

    /**
     * Busca eventos por ator (usuário que executou a ação).
     */
    public Page<SecurityAuditEvent> findByActorUserId(Long actorUserId, Pageable pageable) {
        return publicSchemaUnitOfWork.readOnly(() -> 
                securityAuditEventRepository.findByActorUserId(actorUserId, pageable));
    }

    /**
     * Busca eventos por alvo (usuário afetado pela ação).
     */
    public Page<SecurityAuditEvent> findByTargetUserId(Long targetUserId, Pageable pageable) {
        return publicSchemaUnitOfWork.readOnly(() -> 
                securityAuditEventRepository.findByTargetUserId(targetUserId, pageable));
    }

    /**
     * Busca eventos por conta.
     */
    public Page<SecurityAuditEvent> findByAccountId(Long accountId, Pageable pageable) {
        return publicSchemaUnitOfWork.readOnly(() -> 
                securityAuditEventRepository.findByAccountId(accountId, pageable));
    }

    /**
     * Busca eventos em um período.
     */
    public Page<SecurityAuditEvent> findByOccurredAtBetween(Instant start, Instant end, Pageable pageable) {
        return publicSchemaUnitOfWork.readOnly(() -> 
                securityAuditEventRepository.findByOccurredAtBetween(start, end, pageable));
    }

    /**
     * Busca eventos combinando múltiplos filtros.
     */
    public Page<SecurityAuditEvent> search(
            SecurityAuditActionType actionType,
            Long actorUserId,
            Long targetUserId,
            Long accountId,
            AuditOutcome outcome,
            Instant start,
            Instant end,
            Pageable pageable
    ) {
        return publicSchemaUnitOfWork.readOnly(() -> 
                securityAuditEventRepository.search(
                        actionType, actorUserId, targetUserId, accountId, outcome, start, end, pageable));
    }
}