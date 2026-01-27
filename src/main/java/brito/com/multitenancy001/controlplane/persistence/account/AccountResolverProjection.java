package brito.com.multitenancy001.controlplane.persistence.account;

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
