package brito.com.multitenancy001.tenant.sales.api.dto;

import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Create sale request.
 */
public record SaleCreateRequest(

        @NotNull(message = "saleDate is required")
        Instant saleDate,

        String customerName,
        String customerDocument,
        String customerEmail,
        String customerPhone,

        @NotNull(message = "status is required")
        SaleStatus status,

        @NotEmpty(message = "items is required")
        List<@Valid SaleItemRequest> items
) {
    public SaleCreateRequest {
        if (customerName != null) customerName = customerName.trim();
        if (customerDocument != null) customerDocument = customerDocument.trim();
        if (customerEmail != null) customerEmail = customerEmail.trim();
        if (customerPhone != null) customerPhone = customerPhone.trim();
    }
}