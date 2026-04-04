package brito.com.multitenancy001.tenant.users.app.command;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import brito.com.multitenancy001.infrastructure.publicschema.audit.PublicAuditDispatcher;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de auditoria do módulo de usuários do tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserAuditService {

    private static final String SCOPE = "TENANT";

    private final PublicAuditDispatcher publicAuditDispatcher;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    @FunctionalInterface
    public interface AuditCallable<T> {
        T call();
    }

    public record Actor(Long userId, String email) {
        public static Actor anonymous() {
            return new Actor(null, null);
        }
    }

    public <T> T auditAttemptSuccessFail(
            SecurityAuditActionType actionType,
            Actor actor,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            Map<String, Object> attemptDetails,
            Map<String, Object> successDetails,
            AuditCallable<T> block
    ) {
        recordAudit(actionType, AuditOutcome.ATTEMPT, actor, targetEmail, targetUserId, accountId, tenantSchema, attemptDetails);

        try {
            T result = block.call();

            Object detailsToPersist = successDetails != null ? successDetails : attemptDetails;
            recordAudit(actionType, AuditOutcome.SUCCESS, actor, targetEmail, targetUserId, accountId, tenantSchema, detailsToPersist);

            return result;
        } catch (ApiException ex) {
            recordAudit(actionType, outcomeFrom(ex), actor, targetEmail, targetUserId, accountId, tenantSchema, failureDetails(ex));
            throw ex;
        } catch (Exception ex) {
            recordAudit(actionType, AuditOutcome.FAILURE, actor, targetEmail, targetUserId, accountId, tenantSchema, unexpectedFailureDetails(ex));
            throw ex;
        }
    }

    public void recordAudit(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            Actor actor,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            Object details
    ) {
        final String detailsJson = toJson(details);

        publicAuditDispatcher.dispatch(() -> {
            try {
                securityAuditService.record(
                        actionType,
                        outcome,
                        actor == null ? null : actor.email(),
                        actor == null ? null : actor.userId(),
                        targetEmail,
                        targetUserId,
                        accountId,
                        tenantSchema,
                        detailsJson
                );
            } catch (Exception e) {
                log.warn(
                        "Falha ao gravar SecurityAudit (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                        actionType,
                        outcome,
                        accountId,
                        tenantSchema,
                        e.getMessage(),
                        e
                );
            }
        });
    }

    public Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kv == null) {
            return map;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            Object value = kv[i + 1];
            if (key != null) {
                map.put(String.valueOf(key), value);
            }
        }
        return map;
    }

    private String toJson(Object details) {
        if (details == null) {
            return null;
        }

        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) {
            return null;
        }

        return node.toString();
    }

    private AuditOutcome outcomeFrom(ApiException ex) {
        if (ex == null) {
            return AuditOutcome.FAILURE;
        }
        int status = ex.getStatus();
        return (status == 401 || status == 403) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
    }

    private Map<String, Object> failureDetails(ApiException ex) {
        return m(
                "scope", SCOPE,
                "error", ex == null ? null : ex.getError(),
                "status", ex == null ? 0 : ex.getStatus(),
                "message", safeMessage(ex == null ? null : ex.getMessage())
        );
    }

    private Map<String, Object> unexpectedFailureDetails(Exception ex) {
        return m(
                "scope", SCOPE,
                "unexpected", ex == null ? null : ex.getClass().getSimpleName(),
                "message", safeMessage(ex == null ? null : ex.getMessage())
        );
    }

    private String safeMessage(String msg) {
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        return msg.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }
}