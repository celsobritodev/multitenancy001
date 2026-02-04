package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.time.Instant;

public interface AccountResolverProjection {
    Long getId();
    String getSchemaName();

    // necessários para seleção no frontend
    String getSlug();
    String getDisplayName();

    String getStatus();

    // ✅ FIX: o campo no domínio é trialEndAt (não trialEndDate)
    Instant getTrialEndAt();

    String getOrigin();
}
