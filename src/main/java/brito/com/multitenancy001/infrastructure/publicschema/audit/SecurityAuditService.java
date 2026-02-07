package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;

    public void record(SecurityAuditActionType actionType,
                       AuditOutcome outcome,
                       String actorEmail,
                       Long actorUserId,
                       String targetEmail,
                       Long targetUserId,
                       Long accountId,
                       String tenantSchema,
                       String detailsJson) {

        RequestMeta meta = RequestMetaContext.getOrNull();
        String resolvedTenant = StringUtils.hasText(tenantSchema) ? tenantSchema : TenantContext.getOrNull();

        publicUnitOfWork.requiresNew(() -> {
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

    private static InetAddress parseInetOrNull(String rawIp) {
        if (!StringUtils.hasText(rawIp)) return null;
        try {
            return InetAddress.getByName(rawIp);
        } catch (Exception ex) {
            return null;
        }
    }
}
