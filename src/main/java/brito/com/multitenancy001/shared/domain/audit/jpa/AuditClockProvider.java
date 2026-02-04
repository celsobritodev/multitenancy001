package brito.com.multitenancy001.shared.domain.audit.jpa;

import java.time.Instant;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.time.AppClock;

/**
 * Bean Spring que "liga" AppClock no mundo JPA EntityListener via holder estático.
 */
@Component
public class AuditClockProvider {

    private final AppClock appClock;

    public AuditClockProvider(AppClock appClock) {
        this.appClock = appClock;
    }

    @PostConstruct
    void register() {
        AuditClockProviders.setProvider(this);
    }

    @PreDestroy
    void unregister() {
        AuditClockProviders.clear();
    }

    public Instant now() {
        return appClock.instant();
    }
}

