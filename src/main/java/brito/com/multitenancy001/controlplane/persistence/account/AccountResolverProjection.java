package brito.com.multitenancy001.controlplane.persistence.account;

import java.time.LocalDateTime;

/**
 * Projection mínima para resolver account sem expor a entidade Account.
 * Importante: retorna origin/status como String para evitar shared depender de enums do ControlPlane.
 */
public interface AccountResolverProjection {

    Long getId();

    String getSchemaName();

    String getStatus();     // ex.: "ACTIVE", "FREE_TRIAL", ...

    String getOrigin();     // ex.: "BUILT_IN", "ADMIN", ...

    LocalDateTime getTrialEndDate();
}
