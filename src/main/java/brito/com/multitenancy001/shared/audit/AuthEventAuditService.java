package brito.com.multitenancy001.shared.audit;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEvent;
import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventRepository;
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
public class AuthEventAuditService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AuthEventRepository authEventRepository;
    private final AppClock appClock;

    public void record(String authDomain,
                       String eventType,
                       String outcome,
                       String principalEmail,
                       Long principalUserId,
                       Long accountId,
                       String tenantSchema,
                       String detailsJson) {

        RequestMeta meta = RequestMetaContext.getOrNull();
        String resolvedTenant = StringUtils.hasText(tenantSchema) ? tenantSchema : TenantContext.getOrNull();

        publicUnitOfWork.requiresNew(() -> {
            AuthEvent ev = new AuthEvent();

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

            ev.setAuthDomain(authDomain);
            ev.setEventType(eventType);
            ev.setOutcome(outcome);

            ev.setPrincipalEmail(principalEmail);
            ev.setPrincipalUserId(principalUserId);

            ev.setAccountId(accountId);
            ev.setTenantSchema(resolvedTenant);

            ev.setDetailsJson(detailsJson);

            authEventRepository.save(ev);
        });
    }
}
