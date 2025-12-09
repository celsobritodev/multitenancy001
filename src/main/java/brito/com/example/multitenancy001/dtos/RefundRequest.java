package brito.com.example.multitenancy001.dtos;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record RefundRequest(
    @PositiveOrZero BigDecimal amount,
    String reason
) {}