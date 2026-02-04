package brito.com.multitenancy001.shared.domain.audit.jpa;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;
import brito.com.multitenancy001.shared.security.AuthenticatedPrincipal;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuditActorProvider {

    @PostConstruct
    void register() {
        AuditActorProviders.setProvider(this);
    }

    @PreDestroy
    void unregister() {
        AuditActorProviders.clear();
    }

    public AuditActor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal p)) {
            return AuditActor.system();
        }

        return new AuditActor(p.getUserId(), p.getEmail());
    }
}

