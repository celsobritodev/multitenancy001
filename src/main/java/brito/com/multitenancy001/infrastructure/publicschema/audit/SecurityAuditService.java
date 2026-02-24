package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço central de auditoria de segurança (append-only) gravado em PUBLIC schema.
 *
 * <p><b>Objetivo:</b></p>
 * <ul>
 *   <li>Registrar eventos sensíveis de segurança/billing/users com outcome: ATTEMPT/SUCCESS/DENIED/FAILURE.</li>
 *   <li>Produzir trilha SOC2-like para investigação e compliance.</li>
 * </ul>
 *
 * <p><b>Regras:</b></p>
 * <ul>
 *   <li>Deve ser chamado por AppServices/UseCases (não Controller).</li>
 *   <li>Details deve ser JSON estruturado e nunca conter segredos (tokens, senhas, hashes, etc.).</li>
 *   <li>occurredAt deve vir do AppClock (Instant -> timestamptz).</li>
 * </ul>
 *
 * <p><b>Isolamento transacional:</b>
 * Auditoria PUBLIC não deve "entrar" em TX TENANT. Por isso usamos REQUIRES_NEW e
 * {@code transactionManager = "publicTransactionManager"}.</p>
 *
 * <p><b>Diagnóstico:</b>
 * Este serviço loga quando é invocado com transação já ativa no thread (indicando nesting).</p>
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra um evento append-only de segurança.
     *
     * <p><b>TX:</b> sempre PUBLIC e sempre REQUIRES_NEW para não misturar com TENANT.</p>
     */
    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRES_NEW
    )
    public void record(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            String actorEmail,
            Long actorUserId,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            String detailsJson
    ) {
        logIfCalledWithActiveTx("record", actionType, outcome, accountId, tenantSchema);
        doRecord(actionType, outcome, actorEmail, actorUserId, targetEmail, targetUserId, accountId, tenantSchema, detailsJson);
    }

    /**
     * Helper para passar objeto estruturado e converter em JSON.
     *
     * <p><b>TX:</b> PUBLIC + REQUIRES_NEW.</p>
     */
    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRES_NEW
    )
    public void recordStructured(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            String actorEmail,
            Long actorUserId,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            Object details
    ) {
        logIfCalledWithActiveTx("recordStructured", actionType, outcome, accountId, tenantSchema);

        String detailsJson = (details == null)
                ? null
                : jsonDetailsMapper.toJsonNode(details).toString();

        doRecord(actionType, outcome, actorEmail, actorUserId, targetEmail, targetUserId, accountId, tenantSchema, detailsJson);
    }

    /**
     * Implementação interna (SEM anotação transacional) para evitar confusão com self-invocation
     * e manter a regra: "uma fronteira transacional por método público".
     */
    private void doRecord(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            String actorEmail,
            Long actorUserId,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            String detailsJson
    ) {
        Instant now = appClock.instant();

        RequestMeta meta = RequestMetaContext.getOrNull();
        UUID requestId = meta != null ? meta.requestId() : null;
        InetAddress ip = meta != null ? parseInetAddressOrNull(meta.ip()) : null;
        String userAgent = meta != null ? meta.userAgent() : null;
        String method = meta != null ? meta.method() : null;
        String uri = meta != null ? meta.uri() : null;

        SecurityAuditEvent e = new SecurityAuditEvent();
        e.setOccurredAt(now);

        e.setRequestId(requestId);
        e.setMethod(trimOrNull(method));
        e.setUri(trimOrNull(uri));
        e.setIp(ip);
        e.setUserAgent(trimOrNull(userAgent));

        e.setActionType(actionType);
        e.setOutcome(outcome);

        e.setActorEmail(normalizeEmailOrNull(actorEmail));
        e.setActorUserId(actorUserId);

        e.setTargetEmail(normalizeEmailOrNull(targetEmail));
        e.setTargetUserId(targetUserId);

        e.setAccountId(accountId);
        e.setTenantSchema(trimOrNull(tenantSchema));

        e.setDetailsJson(normalizeDetailsJson(detailsJson));

        if (log.isDebugEnabled()) {
            log.debug("🧾 Audit save | actionType={} | outcome={} | accountId={} | tenantSchema={} | requestId={}",
                    actionType, outcome, accountId, tenantSchema, requestId);
        }

        securityAuditEventRepository.save(e);
    }

    private static void logIfCalledWithActiveTx(
            String method,
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            Long accountId,
            String tenantSchema
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();

        log.warn("⚠️ SecurityAuditService.{} chamado com transação já ativa no thread (possível nesting TENANT->PUBLIC/JDBC) | actionType={} | outcome={} | accountId={} | tenantSchema={} | resources={}",
                method, actionType, outcome, accountId, tenantSchema, summarizeResources(resources));
    }

    private static String summarizeResources(Map<Object, Object> resources) {
        if (resources == null || resources.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var e : resources.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            Object k = e.getKey();
            Object v = e.getValue();
            sb.append(k == null ? "null" : k.getClass().getName())
              .append("->")
              .append(v == null ? "null" : v.getClass().getName());
        }
        sb.append("]");
        return sb.toString();
    }

    private static InetAddress parseInetAddressOrNull(String ip) {
        if (!StringUtils.hasText(ip)) return null;
        try {
            return InetAddress.getByName(ip.trim());
        } catch (Exception ex) {
            return null; // auditoria nunca deve falhar por IP inválido
        }
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeEmailOrNull(String email) {
        if (!StringUtils.hasText(email)) return null;
        String t = email.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeDetailsJson(String json) {
        if (!StringUtils.hasText(json)) return null;
        String t = json.trim();
        if (t.isEmpty()) return null;

        if (!(t.startsWith("{") || t.startsWith("["))) {
            return "{\"raw\":\"" + escapeJsonString(t) + "\"}";
        }
        return t;
    }

    private static String escapeJsonString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}