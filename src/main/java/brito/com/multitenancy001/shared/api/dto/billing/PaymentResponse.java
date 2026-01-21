package brito.com.multitenancy001.shared.api.dto.billing;

import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long accountId,
        BigDecimal amount,
        LocalDateTime paymentDate,
        LocalDateTime validUntil,
        PaymentStatus status,
        String transactionId,
        PaymentMethod paymentMethod,
        PaymentGateway paymentGateway,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
