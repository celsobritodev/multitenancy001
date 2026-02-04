package brito.com.multitenancy001.shared.audit;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditEvent;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditEventRepository;
import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;

    public void record(String actionType,
                       String outcome,
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

            // ✅ Fonte única de tempo: AppClock
            Instant occurredAt = appClock.instant();
            ev.setOccurredAt(occurredAt);

            if (meta != null) {
                ev.setRequestId(meta.requestId());
                ev.setMethod(meta.method());
                ev.setUri(meta.uri());
                ev.setIp(meta.ip());
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
}

