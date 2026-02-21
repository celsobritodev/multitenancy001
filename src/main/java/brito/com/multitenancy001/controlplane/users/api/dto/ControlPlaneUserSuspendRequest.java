package brito.com.multitenancy001.controlplane.users.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request para suspender/restaurar suspensão administrativa de um usuário do Control Plane.
 *
 * Regras:
 * - reason é opcional, mas recomendado (SOC2-like).
 * - usado em USER_SUSPENDED / USER_RESTORED.
 */
public record ControlPlaneUserSuspendRequest(
        @Size(max = 300, message = "reason deve ter no máximo 300 caracteres")
        String reason
) {}