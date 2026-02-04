package brito.com.multitenancy001.shared.api.dto.billing;

import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long accountId,

        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentGateway paymentGateway,
        PaymentStatus paymentStatus,

        String description,

        Instant paidAt,
        Instant validUntil,
        Instant refundedAt,

        Instant createdAt,
        Instant updatedAt
) {}

