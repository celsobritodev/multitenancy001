package brito.com.multitenancy001.controlplane.accounts.app.command;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;

/**
 * Command para mudança de status da Account (Control Plane).
 *
 * Regras:
 * - status é obrigatório.
 * - reason e origin são opcionais, mas recomendados para auditoria.
 *
 * Origin sugeridos:
 * - "billing_job"
 * - "admin_api"
 * - "system"
 */
public record AccountStatusChangeCommand(
        AccountStatus status,
        String reason,
        String origin
) {
    public AccountStatusChangeCommand(AccountStatus status) {
        /* Construtor de compatibilidade para chamadas antigas. */
        this(status, null, null);
    }
}