package brito.com.multitenancy001.tenant.users.app.audit;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.audit.AuditDetails;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Recorder de auditoria de segurança para operações sensíveis em Users (TENANT).
 *
 * Objetivo:
 * - Centralizar chamadas ao SecurityAuditService (append-only).
 * - Padronizar details em JSON via Map + JsonDetailsMapper.
 * - Garantir campos: actor, target, accountId, tenantSchema, correlationId.
 *
 * Regras:
 * - Deve ser chamado por Application Services (não por controllers).
 * - Não logar segredos (senha/JWT/refresh token).
 */
@Service
@RequiredArgsConstructor
public class TenantUserSecurityAuditRecorder {

    private static final String SCOPE = "tenant.users";

    private final TenantRequestIdentityService requestIdentity;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    public void recordAttempt(SecurityAuditActionType type, Long targetUserId, String targetEmail, Map<String, Object> details) {
        /* Registra tentativa (ATTEMPT) para ação sensível em users. */
        record(type, AuditOutcome.ATTEMPT, targetUserId, targetEmail, details);
    }

    public void recordSuccess(SecurityAuditActionType type, Long targetUserId, String targetEmail, Map<String, Object> details) {
        /* Registra sucesso (SUCCESS) para ação sensível em users. */
        record(type, AuditOutcome.SUCCESS, targetUserId, targetEmail, details);
    }

    public void recordDenied(SecurityAuditActionType type, Long targetUserId, String targetEmail, Map<String, Object> details) {
        /* Registra negação (DENIED) para ação sensível em users. */
        record(type, AuditOutcome.DENIED, targetUserId, targetEmail, details);
    }

    public void recordFailure(SecurityAuditActionType type, Long targetUserId, String targetEmail, Map<String, Object> details) {
        /* Registra falha (FAILURE) para ação sensível em users. */
        record(type, AuditOutcome.FAILURE, targetUserId, targetEmail, details);
    }

    public Map<String, Object> baseDetails(String event, Long targetUserId, String targetEmail) {
        /* Cria details base padronizado para users. */
        Long actorUserId = requestIdentity.getCurrentUserId();
        String actorEmail = requestIdentity.getCurrentEmail();

        Map<String, Object> d = AuditDetails.base(SCOPE, event);
        AuditDetails.withActor(d, actorUserId, actorEmail);
        AuditDetails.withTarget(d, targetUserId, targetEmail);
        return d;
    }

    private void record(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            Long targetUserId,
            String targetEmail,
            Map<String, Object> details
    ) {
        /* Grava evento append-only no public schema via SecurityAuditService. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        Long actorUserId = requestIdentity.getCurrentUserId();
        String actorEmail = requestIdentity.getCurrentEmail();

        String detailsJson = toJson(details);

        securityAuditService.record(
                actionType,
                outcome,
                actorEmail,
                actorUserId,
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                detailsJson
        );
    }

    private String toJson(Object details) {
        /* Converte details (Map/record/String) em JSON string compatível com jsonb. */
        if (details == null) return null;
        return jsonDetailsMapper.toJsonNode(details).toString();
    }
}