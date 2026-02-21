package brito.com.multitenancy001.tenant.payments.app.audit;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.audit.AuditDetails;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recorder de auditoria de segurança para Billing/Payments (TENANT).
 *
 * Objetivo:
 * - Auditar eventos relevantes de billing (criação e mudança de status).
 * - Padronizar details via Map + JsonDetailsMapper.
 *
 * Regras:
 * - Não registrar PAN/cartão, tokens de PSP, payloads sensíveis.
 * - Preferir IDs e status, valores agregados e referências.
 */
@Service
@RequiredArgsConstructor
public class TenantBillingSecurityAuditRecorder {

    private static final String SCOPE = "tenant.billing";

    private final TenantRequestIdentityService requestIdentity;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    public void paymentCreatedAttempt(Long paymentId, String providerRef, Map<String, Object> extra) {
        /* Registra ATTEMPT de criação de pagamento. */
        record(SecurityAuditActionType.PAYMENT_CREATED, AuditOutcome.ATTEMPT, details("payment_created", paymentId, providerRef, extra));
    }

    public void paymentCreatedSuccess(Long paymentId, String providerRef, Map<String, Object> extra) {
        /* Registra SUCCESS de criação de pagamento. */
        record(SecurityAuditActionType.PAYMENT_CREATED, AuditOutcome.SUCCESS, details("payment_created", paymentId, providerRef, extra));
    }

    public void paymentCreatedDenied(Long paymentId, String providerRef, Map<String, Object> extra) {
        /* Registra DENIED de criação de pagamento. */
        record(SecurityAuditActionType.PAYMENT_CREATED, AuditOutcome.DENIED, details("payment_created", paymentId, providerRef, extra));
    }

    public void paymentCreatedFailure(Long paymentId, String providerRef, Map<String, Object> extra) {
        /* Registra FAILURE de criação de pagamento. */
        record(SecurityAuditActionType.PAYMENT_CREATED, AuditOutcome.FAILURE, details("payment_created", paymentId, providerRef, extra));
    }

    public void paymentStatusChangedAttempt(Long paymentId, String fromStatus, String toStatus, Map<String, Object> extra) {
        /* Registra ATTEMPT de mudança de status do pagamento. */
        Map<String, Object> d = details("payment_status_changed", paymentId, null, extra);
        d.put("fromStatus", fromStatus);
        d.put("toStatus", toStatus);
        record(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, AuditOutcome.ATTEMPT, d);
    }

    public void paymentStatusChangedSuccess(Long paymentId, String fromStatus, String toStatus, Map<String, Object> extra) {
        /* Registra SUCCESS de mudança de status do pagamento. */
        Map<String, Object> d = details("payment_status_changed", paymentId, null, extra);
        d.put("fromStatus", fromStatus);
        d.put("toStatus", toStatus);
        record(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, AuditOutcome.SUCCESS, d);
    }

    public void paymentStatusChangedDenied(Long paymentId, String fromStatus, String toStatus, Map<String, Object> extra) {
        /* Registra DENIED de mudança de status do pagamento. */
        Map<String, Object> d = details("payment_status_changed", paymentId, null, extra);
        d.put("fromStatus", fromStatus);
        d.put("toStatus", toStatus);
        record(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, AuditOutcome.DENIED, d);
    }

    public void paymentStatusChangedFailure(Long paymentId, String fromStatus, String toStatus, Map<String, Object> extra) {
        /* Registra FAILURE de mudança de status do pagamento. */
        Map<String, Object> d = details("payment_status_changed", paymentId, null, extra);
        d.put("fromStatus", fromStatus);
        d.put("toStatus", toStatus);
        record(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, AuditOutcome.FAILURE, d);
    }

    private void record(SecurityAuditActionType actionType, AuditOutcome outcome, Map<String, Object> details) {
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
                null,
                null,
                accountId,
                tenantSchema,
                detailsJson
        );
    }

    private Map<String, Object> details(String event, Long paymentId, String providerRef, Map<String, Object> extra) {
        /* Monta details base de billing com campos seguros. */
        Map<String, Object> d = AuditDetails.base(SCOPE, event);
        if (paymentId != null) d.put("paymentId", paymentId);
        if (providerRef != null) d.put("providerRef", providerRef);
        if (extra != null && !extra.isEmpty()) d.put("extra", new LinkedHashMap<>(extra));
        return d;
    }

    private String toJson(Object details) {
        /* Converte details (Map/record/String) em JSON string compatível com jsonb. */
        if (details == null) return null;
        return jsonDetailsMapper.toJsonNode(details).toString();
    }
}