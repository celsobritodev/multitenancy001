package brito.com.multitenancy001.tenant.sales.api.dto;

import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        Instant saleDate,
        BigDecimal totalAmount,

        String customerName,
        String customerDocument,
        String customerEmail,
        String customerPhone,

        SaleStatus status,

        List<SaleItemResponse> items
) {}