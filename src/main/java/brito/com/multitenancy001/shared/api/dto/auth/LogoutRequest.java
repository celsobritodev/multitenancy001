package brito.com.multitenancy001.shared.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request de logout forte.
 *
 * Opção B (logout forte):
 * - O servidor revoga a sessão/refresh token no banco (public schema)
 *
 * Campos:
 * - refreshToken: token de refresh atual (obrigatório)
 * - allDevices: se true, revoga todas as sessões do usuário naquele domínio (TENANT/CONTROLPLANE)
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken,
        boolean allDevices
) {}
