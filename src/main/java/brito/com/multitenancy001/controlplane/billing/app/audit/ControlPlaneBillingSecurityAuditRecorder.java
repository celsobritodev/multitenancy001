package brito.com.multitenancy001.controlplane.billing.app.audit;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.audit.AuditDetails;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recorder de auditoria de segurança para Billing/Payments (CONTROL PLANE).
 *
 * Objetivo:
 * - Centralizar auditoria append-only (public schema) usando SecurityAuditService.
 * - Padronizar details como JSON estruturado (Map/record) via JsonDetailsMapper.
 *
 * Regras:
 * - Deve ser chamado por AppServices/UseCases (não Controller).
 * - Nunca registrar segredos (ex.: dados de cartão, tokens de gateway, JWT/refresh token).
 * - Preferir IDs, status, valores agregados e referências seguras.
 *
 * Nota importante (boundary):
 * - ControlPlaneRequestIdentityService expõe apenas accountId e userId.
 * - Logo, este recorder NÃO depende de email do ator. actorEmail será null.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneBillingSecurityAuditRecorder {

    private static final String SCOPE = "controlplane.billing";

    private final ControlPlaneRequestIdentityService requestIdentity;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    public Map<String, Object> baseDetails(String event, Long accountId, String accountEmail) {
        /* Cria details base padronizado para billing (control plane). */
        Long actorUserId = requestIdentity.getCurrentUserId();

        Map<String, Object> d = AuditDetails.base(SCOPE, event);

        // Mantém compatibilidade: grava actorUserId, mas sem email do ator (boundary não fornece).
        d.put("actorUserId", actorUserId);

        // Em billing, o “target” é a account (e opcionalmente email do login)
        if (accountId != null) d.put("accountId", accountId);
        if (accountEmail != null) d.put("accountEmail", accountEmail);

        return d;
    }

    public void recordAttempt(SecurityAuditActionType type, Long accountId, String accountEmail, Map<String, Object> details) {
        /* Registra tentativa (ATTEMPT) para ação sensível em billing. */
        record(type, AuditOutcome.ATTEMPT, accountId, accountEmail, details);
    }

    public void recordSuccess(SecurityAuditActionType type, Long accountId, String accountEmail, Map<String, Object> details) {
        /* Registra sucesso (SUCCESS) para ação sensível em billing. */
        record(type, AuditOutcome.SUCCESS, accountId, accountEmail, details);
    }

    public void recordDenied(SecurityAuditActionType type, Long accountId, String accountEmail, Map<String, Object> details) {
        /* Registra negação (DENIED) para ação sensível em billing. */
        record(type, AuditOutcome.DENIED, accountId, accountEmail, details);
    }

    public void recordFailure(SecurityAuditActionType type, Long accountId, String accountEmail, Map<String, Object> details) {
        /* Registra falha (FAILURE) para ação sensível em billing. */
        record(type, AuditOutcome.FAILURE, accountId, accountEmail, details);
    }

    private void record(SecurityAuditActionType actionType,
                        AuditOutcome outcome,
                        Long accountId,
                        String accountEmail,
                        Map<String, Object> details) {
        /* Grava evento append-only no public schema via SecurityAuditService. */
        Long actorUserId = requestIdentity.getCurrentUserId();

        String detailsJson = toJson(details);

        // Boundary: não temos actorEmail via requestIdentity -> enviar null.
        securityAuditService.record(
                actionType,
                outcome,
                null,          // actorEmail (não disponível no boundary atual)
                actorUserId,
                accountEmail,  // targetEmail (opcional; pode ser null)
                null,          // targetUserId não se aplica aqui
                accountId,     // accountId alvo
                null,          // tenantSchema (billing é public)
                detailsJson
        );
    }

    private String toJson(Object details) {
        /* Converte details (Map/record/String) em JSON string compatível com jsonb. */
        if (details == null) return null;
        return jsonDetailsMapper.toJsonNode(details).toString();
    }

    public static Map<String, Object> safeExtra(Map<String, Object> extra) {
        /* Normaliza extra como LinkedHashMap para estabilidade do JSON. */
        if (extra == null || extra.isEmpty()) return null;
        return new LinkedHashMap<>(extra);
    }
}