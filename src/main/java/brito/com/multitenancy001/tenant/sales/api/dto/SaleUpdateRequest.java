package brito.com.multitenancy001.tenant.sales.api.dto;

import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Update sale request.
 *
 * <p>Atualiza cabeçalho e substitui itens.</p>
 * <p>O vínculo com o customer é feito por {@code customerId}.</p>
 */
public record SaleUpdateRequest(

        @NotNull(message = "saleDate is required")
        Instant saleDate,

        UUID customerId,

        @NotNull(message = "status is required")
        SaleStatus status,

        @NotEmpty(message = "items is required")
        List<@Valid SaleItemRequest> items
) {
}