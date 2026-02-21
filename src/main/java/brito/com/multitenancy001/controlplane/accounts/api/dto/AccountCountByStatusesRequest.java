package brito.com.multitenancy001.controlplane.accounts.api.dto;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request tipado para consultas de contagem de contas por status.
 *
 * Motivação:
 * - Evitar @RequestBody List<Enum> "solto" (contrato frágil e difícil de estender).
 * - Permitir evolução futura (ex.: incluir flags, filtros adicionais, paginação etc).
 */
public record AccountCountByStatusesRequest(

        @NotNull
        @NotEmpty
        List<AccountStatus> statuses

) {
}