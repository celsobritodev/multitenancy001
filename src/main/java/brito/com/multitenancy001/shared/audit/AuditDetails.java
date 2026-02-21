package brito.com.multitenancy001.shared.audit;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder padrão para details (JSON) de auditoria.
 *
 * Objetivo:
 * - Padronizar payloads de auditoria (Map) para virar JSON via JsonDetailsMapper.
 * - Garantir campos recorrentes (scope, requestId/correlationId, alvo, mudanças).
 *
 * Regras:
 * - NUNCA incluir segredos (senha, refresh token, JWT, etc).
 * - Preferir valores primitivos/strings/listas pequenas.
 */
public final class AuditDetails {

    private AuditDetails() {}

    public static Map<String, Object> base(String scope, String event) {
        /* Monta o mapa base com scope/event e correlationId (requestId). */
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scope", scope);
        m.put("event", event);

        RequestMeta meta = RequestMetaContext.getOrNull();
        if (meta != null && meta.requestId() != null) {
            m.put("correlationId", meta.requestId().toString());
        }
        return m;
    }

    public static Map<String, Object> withTarget(Map<String, Object> base, Long targetUserId, String targetEmail) {
        /* Adiciona alvo (target) no mapa base. */
        if (base == null) base = new LinkedHashMap<>();
        if (targetUserId != null) base.put("targetUserId", targetUserId);
        if (targetEmail != null && !targetEmail.isBlank()) base.put("targetEmail", targetEmail);
        return base;
    }

    public static Map<String, Object> withActor(Map<String, Object> base, Long actorUserId, String actorEmail) {
        /* Adiciona ator (actor) no mapa base. */
        if (base == null) base = new LinkedHashMap<>();
        if (actorUserId != null) base.put("actorUserId", actorUserId);
        if (actorEmail != null && !actorEmail.isBlank()) base.put("actorEmail", actorEmail);
        return base;
    }

    public static Map<String, Object> withChanges(Map<String, Object> base, Object changes) {
        /* Adiciona mudanças (delta) no mapa base. */
        if (base == null) base = new LinkedHashMap<>();
        if (changes != null) base.put("changes", changes);
        return base;
    }

    public static Map<String, Object> withReason(Map<String, Object> base, String reason) {
        /* Adiciona reason no mapa base (sem stacktrace). */
        if (base == null) base = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) base.put("reason", reason);
        return base;
    }
}