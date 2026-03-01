// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/SecurityAuditTxWriter.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.infrastructure.persistence.transaction.PublicTransactionTemplateProvider;
import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityAuditTxWriter {

    private final PublicTransactionTemplateProvider publicTx;
    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;

    public void write(SecurityAuditRequestedEvent ev) {
        if (ev == null) return;

        // isola com REQUIRES_NEW no executor oficial do projeto
        publicTx.inPublicRequiresNew(() -> doWrite(ev));
    }

    private void doWrite(SecurityAuditRequestedEvent ev) {
        Instant occurredAt = (ev.occurredAt() != null) ? ev.occurredAt() : appClock.instant();

        RequestMeta meta = RequestMetaContext.getOrNull();
        UUID requestId = meta != null ? meta.requestId() : null;
        InetAddress ip = meta != null ? parseInetAddressOrNull(meta.ip()) : null;
        String userAgent = meta != null ? meta.userAgent() : null;
        String method = meta != null ? meta.method() : null;
        String uri = meta != null ? meta.uri() : null;

        SecurityAuditEvent e = new SecurityAuditEvent();
        e.setOccurredAt(occurredAt);

        e.setRequestId(requestId);
        e.setMethod(trimOrNull(method));
        e.setUri(trimOrNull(uri));
        e.setIp(ip);
        e.setUserAgent(trimOrNull(userAgent));

        e.setActionType(ev.actionType());
        e.setOutcome(ev.outcome());

        e.setActorEmail(normalizeEmailOrNull(ev.actorEmail()));
        e.setActorUserId(ev.actorUserId());

        e.setTargetEmail(normalizeEmailOrNull(ev.targetEmail()));
        e.setTargetUserId(ev.targetUserId());

        e.setAccountId(ev.accountId());
        e.setTenantSchema(trimOrNull(ev.tenantSchema()));

        e.setDetailsJson(normalizeDetailsJson(ev.detailsJson()));

        securityAuditEventRepository.save(e);
    }

    private static InetAddress parseInetAddressOrNull(String ip) {
        if (!StringUtils.hasText(ip)) return null;
        try { return InetAddress.getByName(ip.trim()); } catch (Exception ex) { return null; }
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