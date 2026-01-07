package brito.com.multitenancy001.controlplane.api.dto.billing;



import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
	    Long id,
	    Long accountId,
	    BigDecimal amount,
	    LocalDateTime paymentDate,
	    LocalDateTime validUntil,
	    String status,
	    String transactionId,
	    String paymentMethod,
	    String paymentGateway,
	    String description,
	    LocalDateTime createdAt,
	    LocalDateTime updatedAt
	) {}
