package brito.com.multitenancy001.tenant.auth.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TenantLoginConfirmRequest(
        @NotNull(message = "challengeId é obrigatório")
        UUID challengeId,

        // Preferencial: usar slug
        String slug,

        // Compat opcional (se você quiser confirmar por id)
        Long accountId
) {}
