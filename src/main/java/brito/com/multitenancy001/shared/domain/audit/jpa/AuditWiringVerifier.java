package brito.com.multitenancy001.shared.domain.audit.jpa;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AuditWiringVerifier {

    @PostConstruct
    public void verifyAuditWiring() {
        // FAIL-FAST no startup se o wiring quebrou
        AuditActorProviders.requireRegistered();
        AuditClockProviders.requireRegistered();
    }
}
