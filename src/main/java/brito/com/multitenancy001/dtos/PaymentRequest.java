package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull Long accountId,
    @NotNull @Positive BigDecimal amount,
    @NotNull String paymentMethod,
    String paymentGateway,
    String description,
    String cardToken
) {}