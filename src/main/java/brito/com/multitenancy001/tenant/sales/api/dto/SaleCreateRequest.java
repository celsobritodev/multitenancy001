package brito.com.multitenancy001.tenant.sales.api.dto;

import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Create sale request.
 *
 * <p>Agora a integração com customers é feita por {@code customerId}.</p>
 * <p>Os dados snapshot do customer não devem mais vir do request;
 * eles serão preenchidos automaticamente pelo service.</p>
 */
public record SaleCreateRequest(

        @NotNull(message = "saleDate is required")
        Instant saleDate,

        UUID customerId,

        @NotNull(message = "status is required")
        SaleStatus status,

        @NotEmpty(message = "items is required")
        List<@Valid SaleItemRequest> items
) {
}