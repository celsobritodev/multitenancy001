package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * Serviço central de auditoria de segurança (append-only) gravado em PUBLIC schema.
 *
 * Objetivo:
 * - Registrar eventos sensíveis de segurança/billing/users com outcome: ATTEMPT/SUCCESS/DENIED/FAILURE.
 * - Produzir trilha SOC2-like para investigação e compliance.
 *
 * Regras:
 * - Deve ser chamado por AppServices/UseCases (não Controller).
 * - Details deve ser JSON estruturado e nunca conter segredos (tokens, senhas, hashes, etc.).
 * - occurredAt deve vir do AppClock (Instant -> timestamptz).
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra um evento append-only de segurança.
     */
    @Transactional
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
        /* Comentário do método: cria e persiste um evento append-only com request meta e occurredAt via AppClock. */

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

        // garante JSON válido (se vier null, fica null)
        e.setDetailsJson(normalizeDetailsJson(detailsJson));

        securityAuditEventRepository.save(e);
    }

    /**
     * Helper para quem quer passar Map/record diretamente e deixar o serviço converter em JSON.
     */
    @Transactional
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
        /* Comentário do método: converte details tipado (Map/record) em JSON e delega para record(). */

        String detailsJson = (details == null)
                ? null
                : jsonDetailsMapper.toJsonNode(details).toString();

        record(
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

    private static InetAddress parseInetAddressOrNull(String ip) {
        /* Comentário do método: converte String IP para InetAddress de forma defensiva. */
        if (!StringUtils.hasText(ip)) return null;
        try {
            return InetAddress.getByName(ip.trim());
        } catch (Exception ex) {
            // auditoria nunca deve falhar por IP inválido
            return null;
        }
    }

    private static String trimOrNull(String s) {
        /* Comentário do método: trim defensivo para evitar lixo em auditoria. */
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeEmailOrNull(String email) {
        /* Comentário do método: normaliza email para auditoria (minúsculo + trim). */
        if (!StringUtils.hasText(email)) return null;
        String t = email.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeDetailsJson(String json) {
        /* Comentário do método: valida minimamente o formato para não persistir "toString()" acidental. */
        if (!StringUtils.hasText(json)) return null;
        String t = json.trim();
        if (t.isEmpty()) return null;

        if (!(t.startsWith("{") || t.startsWith("["))) {
            return "{\"raw\":\"" + escapeJsonString(t) + "\"}";
        }
        return t;
    }

    private static String escapeJsonString(String s) {
        /* Comentário do método: escape básico para embutir texto em JSON. */
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}