package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.time.AppClock;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço de auditoria de segurança (append-only).
 *
 * SOC2-like:
 * - Registra ações sensíveis (CRUD de usuários, roles/perms, reset/troca de senha, ownership transfer).
 * - Details estruturado em JSON (motivo, alvo, mudanças, deltas).
 * - Captura IP/UA/correlationId via RequestMeta (já persistido no evento).
 *
 * Regras:
 * - NÃO logar segredos (senha, refresh token, JWT, etc).
 * - Deve ser chamado a partir de AppServices/UseCases (não do Controller).
 * - Mantém compatibilidade com a assinatura `record(...)` existente.
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * API de baixo nível (mantida) — grava um evento de auditoria com JSON pronto.
     */
    public void record(SecurityAuditActionType actionType,
                       AuditOutcome outcome,
                       String actorEmail,
                       Long actorUserId,
                       String targetEmail,
                       Long targetUserId,
                       Long accountId,
                       String tenantSchema,
                       String detailsJson) {

        /* Resolve RequestMeta e tenant schema efetivo antes de persistir. */
        RequestMeta meta = RequestMetaContext.getOrNull();
        String resolvedTenant = StringUtils.hasText(tenantSchema)
                ? tenantSchema
                : TenantContext.getOrNull();

        publicSchemaUnitOfWork.requiresNew(() -> {
            /* Persiste evento append-only em transação REQUIRES_NEW (não polui tx do caso de uso). */
            SecurityAuditEvent ev = new SecurityAuditEvent();

            Instant occurredAt = appClock.instant();
            ev.setOccurredAt(occurredAt);

            if (meta != null) {
                ev.setRequestId(meta.requestId());
                ev.setMethod(meta.method());
                ev.setUri(meta.uri());
                ev.setIp(parseInetOrNull(meta.ip()));
                ev.setUserAgent(meta.userAgent());
            }

            ev.setActionType(actionType);
            ev.setOutcome(outcome);

            ev.setActorEmail(actorEmail);
            ev.setActorUserId(actorUserId);

            ev.setTargetEmail(targetEmail);
            ev.setTargetUserId(targetUserId);

            ev.setAccountId(accountId);
            ev.setTenantSchema(resolvedTenant);

            ev.setDetailsJson(detailsJson);

            securityAuditEventRepository.save(ev);
        });
    }

    // =========================================================
    // Convenience API (SOC2-like)
    // =========================================================

    public void success(SecurityAuditActionType actionType,
                        String actorEmail,
                        Long actorUserId,
                        String targetEmail,
                        Long targetUserId,
                        Long accountId,
                        String tenantSchema,
                        Map<String, Object> details) {
        /* Registra ação sensível com outcome SUCCESS. */
        record(
                actionType,
                AuditOutcome.SUCCESS,
                actorEmail,
                actorUserId,
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                toDetailsJson(details)
        );
    }

    public void failure(SecurityAuditActionType actionType,
                        String actorEmail,
                        Long actorUserId,
                        String targetEmail,
                        Long targetUserId,
                        Long accountId,
                        String tenantSchema,
                        Map<String, Object> details) {
        /* Registra ação sensível com outcome FAILURE (sem segredos). */
        record(
                actionType,
                AuditOutcome.FAILURE,
                actorEmail,
                actorUserId,
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                toDetailsJson(details)
        );
    }

    // =========================
    // CRUD de usuários (SOC2)
    // =========================

    public void userCreated(String actorEmail, Long actorUserId,
                            String targetEmail, Long targetUserId,
                            Long accountId, String tenantSchema,
                            String reason) {
        success(SecurityAuditActionType.USER_CREATED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("operation", "create")));
    }

    public void userUpdated(String actorEmail, Long actorUserId,
                            String targetEmail, Long targetUserId,
                            Long accountId, String tenantSchema,
                            String reason,
                            Map<String, Object> changes) {
        success(SecurityAuditActionType.USER_UPDATED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("operation", "update", "changes", changes)));
    }

    public void userSuspended(String actorEmail, Long actorUserId,
                              String targetEmail, Long targetUserId,
                              Long accountId, String tenantSchema,
                              String reason) {
        success(SecurityAuditActionType.USER_SUSPENDED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("operation", "suspend")));
    }

    public void userRestored(String actorEmail, Long actorUserId,
                             String targetEmail, Long targetUserId,
                             Long accountId, String tenantSchema,
                             String reason) {
        success(SecurityAuditActionType.USER_RESTORED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("operation", "restore")));
    }

    public void userSoftDeleted(String actorEmail, Long actorUserId,
                                String targetEmail, Long targetUserId,
                                Long accountId, String tenantSchema,
                                String reason) {
        success(SecurityAuditActionType.USER_SOFT_DELETED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("operation", "soft_delete")));
    }

    public void userSoftRestored(String actorEmail, Long actorUserId,
                                 String targetEmail, Long targetUserId,
                                 Long accountId, String tenantSchema,
                                 String reason) {
        success(SecurityAuditActionType.USER_SOFT_RESTORED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("operation", "soft_restore")));
    }

    // =========================
    // Mudanças sensíveis
    // =========================

    public void roleChanged(String actorEmail, Long actorUserId,
                            String targetEmail, Long targetUserId,
                            Long accountId, String tenantSchema,
                            String reason,
                            String roleBefore, String roleAfter) {
        success(SecurityAuditActionType.ROLE_CHANGED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("roleBefore", roleBefore, "roleAfter", roleAfter)));
    }

    public void permissionsChanged(String actorEmail, Long actorUserId,
                                   String targetEmail, Long targetUserId,
                                   Long accountId, String tenantSchema,
                                   String reason,
                                   List<String> permissionsAdded,
                                   List<String> permissionsRemoved) {
        success(SecurityAuditActionType.PERMISSIONS_CHANGED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m(
                        "permissionsAdded", permissionsAdded,
                        "permissionsRemoved", permissionsRemoved
                )));
    }

    public void passwordResetRequested(String actorEmail, Long actorUserId,
                                       String targetEmail, Long targetUserId,
                                       Long accountId, String tenantSchema,
                                       String reason) {
        success(SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("flow", "reset_requested")));
    }

    public void passwordResetCompleted(String actorEmail, Long actorUserId,
                                       String targetEmail, Long targetUserId,
                                       Long accountId, String tenantSchema,
                                       String reason) {
        success(SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("flow", "reset_completed")));
    }

    public void passwordChanged(String actorEmail, Long actorUserId,
                                String targetEmail, Long targetUserId,
                                Long accountId, String tenantSchema,
                                String reason) {
        success(SecurityAuditActionType.PASSWORD_CHANGED,
                actorEmail, actorUserId,
                targetEmail, targetUserId,
                accountId, tenantSchema,
                details(reason, m("flow", "password_changed")));
    }

    public void ownershipTransferred(String actorEmail, Long actorUserId,
                                     Long accountId, String tenantSchema,
                                     String reason,
                                     Long fromUserId, String fromEmail,
                                     Long toUserId, String toEmail) {
        success(SecurityAuditActionType.OWNERSHIP_TRANSFERRED,
                actorEmail, actorUserId,
                null, null,
                accountId, tenantSchema,
                details(reason, m(
                        "fromUserId", fromUserId,
                        "fromEmail", fromEmail,
                        "toUserId", toUserId,
                        "toEmail", toEmail
                )));
    }

    // =========================================================
    // Internals
    // =========================================================

    private String toDetailsJson(Map<String, Object> details) {
        /* Serializa details para JSON usando JsonDetailsMapper (JsonNode -> String). */
        if (details == null || details.isEmpty()) return null;

        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) return null;

        return node.toString();
    }

    private static Map<String, Object> details(String reason, Map<String, Object> more) {
        /* Monta details padrão com reason (quando existir) + extras. */
        Map<String, Object> m = new LinkedHashMap<>();
        if (StringUtils.hasText(reason)) m.put("reason", reason);
        if (more != null && !more.isEmpty()) m.putAll(more);
        return m;
    }

    private static Map<String, Object> m(Object... kv) {
        /* Cria Map em pares key/value, preservando ordem (bom para leitura de logs). */
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k != null) m.put(String.valueOf(k), v);
        }
        return m;
    }

    private static InetAddress parseInetOrNull(String rawIp) {
        /* Converte IP string para InetAddress, tolerando valores inválidos. */
        if (!StringUtils.hasText(rawIp)) return null;
        try {
            return InetAddress.getByName(rawIp);
        } catch (Exception ex) {
            return null;
        }
    }
}