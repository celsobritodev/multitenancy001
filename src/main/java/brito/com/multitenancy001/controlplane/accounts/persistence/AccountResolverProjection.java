package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.time.Instant;

public interface AccountResolverProjection {
    Long getId();
    String getTenantSchema();

    String getSlug();
    String getDisplayName();

    String getStatus();

    Instant getTrialEndAt();

    String getOrigin();
}
