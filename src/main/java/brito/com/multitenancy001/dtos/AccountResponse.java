package brito.com.multitenancy001.dtos;



import java.time.LocalDateTime;

public record AccountResponse(
	    Long id,
	    String name,
	    String schemaName,
	    String status,
	    LocalDateTime createdAt,
	    LocalDateTime trialEndDate,
	    AdminUserResponse adminUser
	) {}
