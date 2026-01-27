package brito.com.multitenancy001.shared.persistence.audit;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.shared.domain.audit.AuditActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuditActorProvider {

    public AuditActor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUserContext ctx)) {
            return AuditActor.system();
        }

        return new AuditActor(ctx.getUserId(), ctx.getEmail());
    }
}
