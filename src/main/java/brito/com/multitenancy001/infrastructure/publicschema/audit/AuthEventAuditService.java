package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthEventAuditService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AuthEventRepository authEventRepository;
    private final AppClock appClock;

    public void record(AuthDomain authDomain,
                       AuthEventType eventType,
                       AuditOutcome outcome,
                       String principalEmail,
                       Long principalUserId,
                       Long accountId,
                       String tenantSchema,
                       String detailsJson) {

        RequestMeta meta = RequestMetaContext.getOrNull();
        String resolvedTenant = StringUtils.hasText(tenantSchema) ? tenantSchema : TenantContext.getOrNull();

        publicUnitOfWork.requiresNew(() -> {
            AuthEvent ev = new AuthEvent();

            Instant occurredAt = appClock.instant();
            ev.setOccurredAt(occurredAt);

            if (meta != null) {
                ev.setRequestId(meta.requestId());
                ev.setMethod(meta.method());
                ev.setUri(meta.uri());
                ev.setIp(parseInetOrNull(meta.ip()));
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

    private static InetAddress parseInetOrNull(String rawIp) {
        if (!StringUtils.hasText(rawIp)) return null;
        try {
            return InetAddress.getByName(rawIp);
        } catch (Exception ex) {
            return null;
        }
    }
}
