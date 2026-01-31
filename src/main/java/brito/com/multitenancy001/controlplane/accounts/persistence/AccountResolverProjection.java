package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.time.LocalDateTime;

public interface AccountResolverProjection {
    Long getId();
    String getSchemaName();

    // ✅ necessários para seleção no frontend
    String getSlug();
    String getDisplayName();

    String getStatus();
    LocalDateTime getTrialEndDate();
    String getOrigin();
}
